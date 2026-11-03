/***********************************************************************
 * This file is part of iDempiere ERP Open Source                      *
 * http://www.idempiere.org                                            *
 *                                                                     *
 * Copyright (C) Contributors                                          *
 *                                                                     *
 * This program is free software; you can redistribute it and/or       *
 * modify it under the terms of the GNU General Public License         *
 * as published by the Free Software Foundation; either version 2      *
 * of the License, or (at your option) any later version.              *
 *                                                                     *
 * This program is distributed in the hope that it will be useful,     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of      *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the        *
 * GNU General Public License for more details.                        *
 *                                                                     *
 * You should have received a copy of the GNU General Public License   *
 * along with this program; if not, write to the Free Software         *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,          *
 * MA 02110-1301, USA.                                                 *
 **********************************************************************/
package org.idempiere.test.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAsset;
import org.compiere.model.MAssetAcct;
import org.compiere.model.MAssetAddition;
import org.compiere.model.MAssetChange;
import org.compiere.model.MAssetDisposed;
import org.compiere.model.MAssetGroupAcct;
import org.compiere.model.MDepreciationWorkfile;
import org.compiere.model.Query;
import org.compiere.model.X_A_Asset_Acct;
import org.compiere.model.X_A_Asset_Disposed;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfo;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.wf.MWorkflow;
import org.idempiere.test.AbstractTestCase;
import org.idempiere.test.DictionaryIDs;
import org.junit.jupiter.api.Test;

/**
 * Tests for Prorated Asset Disposal (A_Disposed_Method = {@link X_A_Asset_Disposed#A_DISPOSED_METHOD_Prorated}).
 * <p>
 * Every test creates its own asset inside the test transaction (rolled back by {@link AbstractTestCase}),
 * so no shared GardenWorld record is modified.
 * <p>
 * Prerequisite: the A_Asset_Acct.ZZDisposedProrated, A_Asset_Acct.ZZDisposalProratedAdj_Acct,
 * A_Depreciation_Exp.ZZ_DailyRate and A_Depreciation_Exp.ZZMonthlyRate columns must exist in the database.
 */
public class ProratedAssetDisposalTest extends AbstractTestCase {

	private static final BigDecimal ASSET_COST = new BigDecimal("12000.00");
	private static final BigDecimal SALVAGE = new BigDecimal("2000.00");
	private static final BigDecimal DAILY_RATE = new BigDecimal("30.00");
	private static final BigDecimal MONTHLY_RATE = new BigDecimal("912.50");

	/**
	 * Create and complete an asset addition (cost 12000, salvage 2000) and give its depreciation
	 * schedule the daily/monthly rates that the prorated disposal reads from A_Depreciation_Exp.
	 */
	private MAsset createDepreciatedAsset(String value) {
		Properties ctx = Env.getCtx();
		String trxName = getTrxName();

		for (MAssetGroupAcct aga : MAssetGroupAcct.forA_Asset_Group_ID(ctx, DictionaryIDs.A_Asset_Group.EQUIPMENT.id, null, trxName)) {
			aga.setUseLifeMonths(18);
			aga.setUseLifeYears(Env.ZERO);
			aga.setUseLifeMonths_F(18);
			aga.setUseLifeYears_F(Env.ZERO);
			aga.saveEx();
		}

		MAsset asset = new MAsset(ctx, 0, trxName);
		asset.setValue(value);
		asset.setName(value);
		asset.setA_Asset_Group_ID(DictionaryIDs.A_Asset_Group.EQUIPMENT.id);
		asset.setM_Product_ID(DictionaryIDs.M_Product.ASSET_VEHICLE.id);
		asset.setIsOwned(true);
		asset.setIsDepreciated(true);
		asset.setAssetServiceDate(getLoginDate());
		asset.setUseLifeMonths(18);
		asset.setUseLifeMonths_F(18);
		asset.saveEx();

		MAssetAddition addition = new MAssetAddition(ctx, 0, trxName);
		addition.setA_Asset_ID(asset.getA_Asset_ID());
		addition.setDateDoc(getLoginDate());
		addition.setDateAcct(getLoginDate());
		addition.setA_SourceType(MAssetAddition.A_SOURCETYPE_Manual);
		addition.setAssetAmtEntered(ASSET_COST);
		addition.setAssetSourceAmt(ASSET_COST);
		addition.setA_Salvage_Value(SALVAGE);
		addition.saveEx();

		ProcessInfo info = MWorkflow.runDocumentActionWorkflow(addition, DocAction.ACTION_Complete);
		addition.load(trxName);
		assertFalse(info.isError(), "Error processing asset addition: " + info.getSummary());
		assertEquals(DocAction.STATUS_Completed, addition.getDocStatus(), "Asset addition is not completed");

		int updated = DB.executeUpdateEx("UPDATE A_Depreciation_Exp SET ZZ_DailyRate=?, ZZMonthlyRate=? WHERE A_Asset_ID=?",
				new Object[] { DAILY_RATE, MONTHLY_RATE, asset.getA_Asset_ID() }, trxName);
		assertTrue(updated > 0, "No A_Depreciation_Exp records found to update rates for asset " + asset.getA_Asset_ID());
		return asset;
	}

	/** All asset accounting records created for the asset across all accounting schemas. */
	private List<MAssetAcct> getAssetAccts(MAsset asset) {
		List<MAssetAcct> list = new Query(Env.getCtx(), MAssetAcct.Table_Name, "A_Asset_ID=?", getTrxName())
				.setParameters(asset.getA_Asset_ID())
				.<MAssetAcct>list();
		assertFalse(list.isEmpty(), "No asset accounting found for asset " + asset.getValue());
		return list;
	}

	/** Enable prorated disposal on all accounting schemas of the asset. */
	private void enableProratedDisposal(MAsset asset, boolean includeAdjAcct) {
		for (MAssetAcct acct : getAssetAccts(asset)) {
			acct.setZZDisposedProrated(X_A_Asset_Acct.ZZDISPOSEDPRORATED_Yes);
			if (includeAdjAcct) {
				acct.setZZDisposalProratedAdj_Acct(acct.getA_Depreciation_Acct());
			} else {
				acct.setZZDisposalProratedAdj_Acct(0);
			}
			acct.saveEx();
		}
	}

	/** A prorated disposal document effective on {@code disposedDate}, posted on the login date. */
	private MAssetDisposed newProratedDisposal(MAsset asset, LocalDate disposedDate) {
		MAssetDisposed disposed = new MAssetDisposed(Env.getCtx(), 0, getTrxName());
		disposed.setA_Asset_ID(asset.getA_Asset_ID());
		disposed.setDateDoc(getLoginDate());
		disposed.setDateAcct(getLoginDate());
		disposed.setA_Disposed_Date(Timestamp.valueOf(disposedDate.atStartOfDay()));
		disposed.setA_Disposed_Method(X_A_Asset_Disposed.A_DISPOSED_METHOD_Prorated);
		disposed.saveEx();
		return disposed;
	}

	/** Mid-month date in the month after the login date: Forward calculation. */
	private LocalDate forwardDisposalDate() {
		return getLoginDate().toLocalDateTime().toLocalDate().plusMonths(1).withDayOfMonth(15);
	}

	/**
	 * ZZDisposedProrated = 'N' on the asset accounting forbids prorated disposal.
	 */
	@Test
	public void testProratedDisposalNotAllowedWhenFlagIsNo() {
		MAsset asset = createDepreciatedAsset("test_prorated_flag_n");
		for (MAssetAcct acct : getAssetAccts(asset)) {
			acct.setZZDisposedProrated(X_A_Asset_Acct.ZZDISPOSEDPRORATED_No);
			acct.saveEx();
		}

		MAssetDisposed disposed = newProratedDisposal(asset, forwardDisposalDate());
		AdempiereException e = assertThrows(AdempiereException.class, () -> disposed.prepareIt());
		assertTrue(e.getMessage().contains("ZZDisposedProrated"), "Unexpected message: " + e.getMessage());
	}

	/**
	 * ZZDisposedProrated = 'Y' but ZZDisposalProratedAdj_Acct is defined neither on the asset accounting
	 * nor on the asset group accounting: processing must stop when an adjustment is needed.
	 */
	@Test
	public void testProratedDisposalStopsWhenAdjustmentAccountMissing() {
		MAsset asset = createDepreciatedAsset("test_prorated_no_adj_acct");
		enableProratedDisposal(asset, false);

		MAssetDisposed disposed = newProratedDisposal(asset, forwardDisposalDate());
		AdempiereException e = assertThrows(AdempiereException.class, () -> disposed.prepareIt());
		assertTrue(e.getMessage().contains("ZZDisposalProratedAdj_Acct"), "Unexpected message: " + e.getMessage());
	}

	/**
	 * Forward calculation: the effective disposal date is after the period of DateAcct.
	 * The asset is written off, accumulated depreciation is brought up to the disposal date and
	 * no unprocessed depreciation expense is left after it.
	 */
	@Test
	public void testProratedDisposalForwardCalculation() {
		Properties ctx = Env.getCtx();
		String trxName = getTrxName();

		MAsset asset = createDepreciatedAsset("test_prorated_forward");
		enableProratedDisposal(asset, true);

		LocalDate disposedDate = forwardDisposalDate();
		MAssetDisposed disposed = newProratedDisposal(asset, disposedDate);

		ProcessInfo info = MWorkflow.runDocumentActionWorkflow(disposed, DocAction.ACTION_Complete);
		disposed.load(trxName);
		assertFalse(info.isError(), "Error completing prorated disposal: " + info.getSummary());
		assertEquals(DocAction.STATUS_Completed, disposed.getDocStatus(), "Disposal is not completed");

		// asset change records the full cost and a prorated (partial) accumulated depreciation
		MAssetChange change = new Query(ctx, MAssetChange.Table_Name,
				"A_Asset_ID=? AND ChangeType=? AND C_AcctSchema_ID=?", trxName)
				.setParameters(asset.getA_Asset_ID(), MAssetChange.CHANGETYPE_Disposal, DictionaryIDs.C_AcctSchema.DOLLAR.id)
				.first();
		assertNotNull(change, "Disposal asset change not created");
		assertEquals(0, ASSET_COST.compareTo(change.getAssetValueAmt()), "Disposed amount must be the full cost");
		BigDecimal accum = change.getAssetAccumDepreciationAmt();
		assertTrue(accum.signum() > 0, "Accumulated depreciation up to the disposal date must be positive");
		assertTrue(accum.compareTo(ASSET_COST.subtract(SALVAGE)) <= 0,
				"Accumulated depreciation cannot exceed cost - salvage");
		assertEquals(0, ASSET_COST.subtract(accum).compareTo(change.getAssetBookValueAmt()),
				"Book value must be cost - accumulated depreciation");

		// workfile is written off
		MDepreciationWorkfile wk = MDepreciationWorkfile.get(ctx, asset.getA_Asset_ID(),
				X_A_Asset_Acct.POSTINGTYPE_Actual, trxName, DictionaryIDs.C_AcctSchema.DOLLAR.id);
		assertEquals(0, Env.ZERO.compareTo(wk.getA_Asset_Cost()), "Asset cost must be zero after disposal");
		assertEquals(0, Env.ZERO.compareTo(wk.getA_Accumulated_Depr()), "Accumulated depreciation must be zero after disposal");

		// no unprocessed depreciation expense after the effective disposal date
		int after = DB.getSQLValueEx(trxName,
				"SELECT COUNT(*) FROM A_Depreciation_Exp WHERE A_Asset_ID=? AND Processed='N' AND DateAcct>?",
				asset.getA_Asset_ID(), Timestamp.valueOf(disposedDate.atStartOfDay()));
		assertEquals(0, after, "Unprocessed depreciation expense must not exist after the disposal date");
	}
}
