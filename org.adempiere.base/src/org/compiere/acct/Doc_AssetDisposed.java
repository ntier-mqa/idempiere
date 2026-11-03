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
package org.compiere.acct;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;

import org.compiere.model.I_A_Asset_Acct;
import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAsset;
import org.compiere.model.MAssetAcct;
import org.compiere.model.MAssetChange;
import org.compiere.model.MAssetDisposed;
import org.compiere.model.MAssetGroupAcct;
import org.compiere.model.MDepreciationWorkfile;
import org.compiere.model.MDocType;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.X_A_Asset_Disposed;
import org.compiere.util.Env;

/**
 * Posting for {@link MAssetDisposed} document. DOCBASETYPE_GLDocument.
 * @author Teo_Sarca, SC ARHIPAC SERVICE SRL
 */
public class Doc_AssetDisposed extends Doc
{
	/**
	 * @param as
	 * @param rs
	 * @param trxName
	 */
	public Doc_AssetDisposed (MAcctSchema as, ResultSet rs, String trxName)
	{
		super(as, MAssetDisposed.class, rs, MDocType.DOCBASETYPE_GLDocument, trxName);
	}

	@Override
	protected String loadDocumentDetails()
	{
		return null;
	}
	
	@Override
	public BigDecimal getBalance()
	{
		return Env.ZERO;
	}

	@Override
	public ArrayList<Fact> createFacts(MAcctSchema as)
	{
		MAssetDisposed assetDisp = (MAssetDisposed)getPO();
		
		ArrayList<Fact> facts = new ArrayList<Fact>();
		Fact fact = new Fact(this, as, assetDisp.getPostingType());
		facts.add(fact);
		MAssetChange ac = MAssetChange.get(getCtx(), assetDisp.getA_Asset_ID(), MAssetChange.CHANGETYPE_Disposal,getTrxName(), as.getC_AcctSchema_ID());
		//
		if (X_A_Asset_Disposed.A_DISPOSED_METHOD_PartialRetirement.equals(assetDisp.getA_Disposed_Method())) {
			
		} else if (X_A_Asset_Disposed.A_DISPOSED_METHOD_Prorated.equals(assetDisp.getA_Disposed_Method())) {
			BigDecimal postedDepr = assetDisp.getA_Accumulated_Depr();
			if (as.getC_Currency_ID() != assetDisp.getC_Currency_ID()) {
				MDepreciationWorkfile wk = MDepreciationWorkfile.get(getCtx(), assetDisp.getA_Asset_ID(), assetDisp.getPostingType(), getTrxName(), as.getC_AcctSchema_ID());
				if (wk != null) {
					postedDepr = wk.getA_Accumulated_Depr();
				}
			}
			BigDecimal requiredDepr = ac.getAssetAccumDepreciationAmt();
			BigDecimal adjAmt = requiredDepr.subtract(postedDepr);
			
			if (adjAmt.signum() > 0) {
				// Forward calculation: Post outstanding prorated depreciation
				MAccount adjAcct = getDepreciationAdjustmentAccount(as);
				FactLine fl = fact.createLine(null, adjAcct
						, as.getC_Currency_ID()
						, adjAmt, Env.ZERO);
				if (fl != null) {
					fl.setDescription("Prorated depreciation forward adjustment to disposal date " + assetDisp.getA_Disposed_Date());
				}
				fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Accumdepreciation_Acct, as)
						, as.getC_Currency_ID()
						, Env.ZERO, adjAmt);
			} else if (adjAmt.signum() < 0) {
				// Backward calculation: Reverse excess depreciation
				BigDecimal reversalAmt = adjAmt.negate();
				MAccount adjAcct = getDepreciationAdjustmentAccount(as);
				fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Accumdepreciation_Acct, as)
						, as.getC_Currency_ID()
						, reversalAmt, Env.ZERO);
				FactLine fl = fact.createLine(null, adjAcct
						, as.getC_Currency_ID()
						, Env.ZERO, reversalAmt);
				if (fl != null) {
					fl.setDescription("Prorated depreciation backward reversal past disposal date " + assetDisp.getA_Disposed_Date());
				}
			}
		}
		
		fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Asset_Acct, as)
				, ac.getC_AcctSchema().getC_Currency_ID()
				, Env.ZERO, ac.getAssetValueAmt());
		fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Accumdepreciation_Acct, as)
				, ac.getC_AcctSchema().getC_Currency_ID()
				, ac.getAssetAccumDepreciationAmt(), Env.ZERO);
		fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Disposal_Loss_Acct, as)
				, ac.getC_AcctSchema().getC_Currency_ID()
				, ac.getAssetBookValueAmt(), Env.ZERO);
		//
		return facts;
	}
	
	/**
	 * Get depreciation adjustment account for prorated disposal.
	 * Checks ZZDisposalProratedAdj_Acct on MAssetAcct, then falls back to MAssetGroupAcct.
	 * 
	 * @param as accounting schema
	 * @return MAccount
	 */
	private MAccount getDepreciationAdjustmentAccount(MAcctSchema as)
	{
		MAssetDisposed assetDisp = (MAssetDisposed)getPO();
		MAssetAcct assetAcct = MAssetAcct.forA_Asset_ID(getCtx(), as.get_ID(), assetDisp.getA_Asset_ID(), assetDisp.getPostingType(), assetDisp.getDateAcct(), getTrxName());
		int acctId = 0;
		if (assetAcct != null)
		{
			acctId = assetAcct.getZZDisposalProratedAdj_Acct();
		}
		if (acctId <= 0)
		{
			MAsset asset = assetDisp.getAsset();
			if (asset != null && asset.getA_Asset_Group_ID() > 0)
			{
				MAssetGroupAcct grpAcct = MAssetGroupAcct.forA_Asset_Group_ID(getCtx(), asset.getA_Asset_Group_ID(), assetDisp.getPostingType(), as.get_ID());
				if (grpAcct != null)
				{
					acctId = grpAcct.getZZDisposalProratedAdj_Acct();
				}
			}
		}
		if (acctId > 0)
		{
			return MAccount.get(getCtx(), acctId);
		}
		throw new AdempiereException("Account " + I_A_Asset_Acct.COLUMNNAME_ZZDisposalProratedAdj_Acct 
				+ " is not defined for asset (ID=" + assetDisp.getA_Asset_ID() + ", AcctSchema=" + as.getName() + "). Processing stopped.");
	}

	/**
	 * @param accountName
	 * @param as
	 * @return MAccount
	 */
	private MAccount getAccount(String accountName, MAcctSchema as)
	{
		MAssetDisposed assetDisp = (MAssetDisposed)getPO();
		MAssetAcct assetAcct = MAssetAcct.forA_Asset_ID(getCtx(), as.get_ID(), assetDisp.getA_Asset_ID(), assetDisp.getPostingType(), assetDisp.getDateAcct(), getTrxName());
		int account_id = (Integer)assetAcct.get_Value(accountName);
		return MAccount.get(getCtx(), account_id);
	}

}
