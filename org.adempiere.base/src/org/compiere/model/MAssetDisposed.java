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
package org.compiere.model;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.adempiere.exceptions.AdempiereException;
import org.idempiere.fa.exceptions.AssetAlreadyDepreciatedException;
import org.idempiere.fa.exceptions.AssetNotImplementedException;
import org.idempiere.fa.exceptions.AssetNotSupportedException;
import org.idempiere.fa.exceptions.AssetStatusChangedException;
import org.idempiere.fa.util.POCacheLocal;

/**
 * Asset Disposal Model
 * @author Teo Sarca, SC ARHIPAC SERVICE SRL
 */
public class MAssetDisposed extends X_A_Asset_Disposed
implements DocAction
{
	/**
	 * generated serial id
	 */
	private static final long serialVersionUID = 1763997880662445638L;

    /**
     * UUID based Constructor
     * @param ctx  Context
     * @param A_Asset_Disposed_UU  UUID key
     * @param trxName Transaction
     */
    public MAssetDisposed(Properties ctx, String A_Asset_Disposed_UU, String trxName) {
        super(ctx, A_Asset_Disposed_UU, trxName);
		if (Util.isEmpty(A_Asset_Disposed_UU))
			setInitialDefaults();
    }

    /**
     * @param ctx
     * @param A_Asset_Disposed_ID
     * @param trxName
     */
	public MAssetDisposed (Properties ctx, int A_Asset_Disposed_ID, String trxName)
	{
		super (ctx, A_Asset_Disposed_ID, trxName);
		if (A_Asset_Disposed_ID == 0)
			setInitialDefaults();
	}
	
	/**
	 * Set the initial defaults for a new record
	 */
	private void setInitialDefaults() {
		setProcessed (false);
		setProcessing (false);
	}

	/**
	 * Create asset disposal from AR invoice
	 * @param invLine
	 * @return MAssetDisposed
	 */
	public static MAssetDisposed createAssetDisposed (MInvoiceLine invLine) {
		MAssetDisposed assetDisposed = new MAssetDisposed(invLine);
		assetDisposed.dump();
		return assetDisposed;
	}
	
	/**
	 * @param invLine
	 */
	private MAssetDisposed (MInvoiceLine invLine) {
		this(invLine.getCtx(),0,invLine.get_TrxName());
		if (log.isLoggable(Level.FINEST)) log.finest("Entering: Project=" + invLine);
		setAD_Org_ID(invLine.getAD_Org_ID());
		setPostingType(POSTINGTYPE_Actual);
		setDateDoc(invLine.getC_Invoice().getDateInvoiced());
		setDateAcct(invLine.getC_Invoice().getDateInvoiced());
		setA_Disposed_Date(invLine.getC_Invoice().getDateInvoiced());
		setA_Disposed_Method(A_DISPOSED_METHOD_Trade);
		setA_Asset_ID(invLine.getA_Asset_ID());
		set_ValueNoCheck("C_Invoice_ID", invLine.getC_Invoice_ID());
		setM_InvoiceLine(invLine);
		saveEx(invLine.get_TrxName());
	}

	private final POCacheLocal<MInvoiceLine> m_cacheInvoiceLine = POCacheLocal.newInstance(this, MInvoiceLine.class);
	
	/**
	 * @param requery
	 * @return MInvoiceLine
	 */
	public MInvoiceLine getM_InvoiceLine(boolean requery)
	{
		return m_cacheInvoiceLine.get(requery);
	}
	
	/**
	 * Set cached invoice line
	 * @param invLine
	 */
	private void setM_InvoiceLine(MInvoiceLine invLine)
	{
		set_Value("C_InvoiceLine_ID", invLine.get_ID());
		m_cacheInvoiceLine.set(invLine);
	}

	/**
	 * @param ctx
	 * @param rs
	 * @param trxName
	 */
	public MAssetDisposed (Properties ctx, ResultSet rs, String trxName)
	{
		super (ctx, rs, trxName);
	}
	
	/**
	 * @return MAsset
	 */
	public MAsset getAsset()
	{
		return MAsset.get(getCtx(), getA_Asset_ID(), get_TrxName());
	}
	
	@Override
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	processIt
	
	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;

	@Override
	public boolean unlockIt()
	{
		setProcessing(false);
		return true;
	}	//	unlockIt
	
	@Override
	public boolean invalidateIt()
	{
		return false;
	}	//	invalidateIt
	
	@Override
	public String prepareIt()
	{
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
		{
			return DocAction.STATUS_Invalid;
		}
		
		MPeriod.testPeriodOpen(getCtx(), getDateAcct(), MDocType.DOCBASETYPE_GLDocument, getAD_Org_ID());

		updateFromAsset(this);
		saveEx(get_TrxName());
		if (is_Changed())
		{
			throw new AssetStatusChangedException();
		}
		
		// Check that the FA is not just depreciated
		MDepreciationWorkfile assetwk = MDepreciationWorkfile.get(getCtx(), getA_Asset_ID(), getPostingType(), get_TrxName());
		if (!A_DISPOSED_METHOD_Prorated.equals(getA_Disposed_Method()))
		{
			if (assetwk.isDepreciated(getDateAcct()))
			{
				throw new AssetAlreadyDepreciatedException();
			}
			MDepreciationExp.checkExistsNotProcessedEntries(getCtx(), getA_Asset_ID(), getDateAcct(), getPostingType(), get_TrxName());
		}
		else
		{
			checkDisposedProratedAllowed();
			BigDecimal requiredDepr = calculateProratedCumulativeDepreciation(assetwk, getA_Disposed_Date());
			BigDecimal currentDepr = assetwk.getA_Accumulated_Depr() != null ? assetwk.getA_Accumulated_Depr() : Env.ZERO;
			if (requiredDepr.compareTo(currentDepr) != 0)
			{
				checkDepreciationAdjustmentAccount();
			}
		}
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
		{
			return DocAction.STATUS_Invalid;
		}
		//
		m_justPrepared = true;
		setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}	//	prepareIt
	
	@Override
	public boolean approveIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("approveIt - " + toString());
		setIsApproved(true);
		return true;
	}	//	approveIt
	
	@Override
	public boolean rejectIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("rejectIt - " + toString());
		setIsApproved(false);
		return true;
	}	//	rejectIt
	
	@Override
	public String completeIt()
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			m_justPrepared = false;
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}
		
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}
		
		//	Implicit Approval
		if (!isApproved())
			approveIt();
		if (log.isLoggable(Level.INFO)) log.info(toString());
		//
		
		//loading asset
		MAsset asset = getAsset();
		if (log.isLoggable(Level.FINE)) log.fine("asset=" + asset);

		// Activation
		if(!isDisposal())
		{
			String method = getA_Activation_Method();
			if(method.equals(A_ACTIVATION_METHOD_Activation))
			{ // reactivation
				asset.changeStatus(MAsset.A_ASSET_STATUS_Activated, getDateDoc());
			}
			else
			{
				throw new AssetNotSupportedException(COLUMNNAME_A_Activation_Method, method);
			}
		}
		// Preservation/Partial Retirement/etc
		else
		{
			String method = getA_Disposed_Method();
			if (A_DISPOSED_METHOD_Preservation.equals(method))
			{
				asset.changeStatus(MAsset.A_ASSET_STATUS_Preservation, getDateDoc());
			}
			else if (A_DISPOSED_METHOD_Simple.equals(method)
					|| A_DISPOSED_METHOD_Trade.equals(method)
				)
			{
				asset.changeStatus(MAsset.A_ASSET_STATUS_Disposed, null);
				setA_Disposal_Amt(getA_Asset_Cost());
				setA_Accumulated_Depr_Delta(getA_Accumulated_Depr());
				setExpense(getA_Disposal_Amt().subtract(getA_Accumulated_Depr_Delta()));
				createDisposal();
			}
			else if (A_DISPOSED_METHOD_PartialRetirement.equals(method))
			{
				createDisposal();
			}
			else if (A_DISPOSED_METHOD_Prorated.equals(method))
			{
				checkDisposedProratedAllowed();
				asset.changeStatus(MAsset.A_ASSET_STATUS_Disposed, null);
				setA_Disposal_Amt(getA_Asset_Cost());
				MDepreciationWorkfile assetwk = MDepreciationWorkfile.get(getCtx(), getA_Asset_ID(), getPostingType(), get_TrxName());
				BigDecimal requiredDepr = calculateProratedCumulativeDepreciation(assetwk, getA_Disposed_Date());
				BigDecimal currentDepr = assetwk.getA_Accumulated_Depr() != null ? assetwk.getA_Accumulated_Depr() : Env.ZERO;
				if (requiredDepr.compareTo(currentDepr) != 0)
				{
					checkDepreciationAdjustmentAccount();
				}
				setA_Accumulated_Depr_Delta(requiredDepr);
				setExpense(getA_Disposal_Amt().subtract(requiredDepr));
				createDisposal();
			}
			else
			{
				throw new AssetNotSupportedException(COLUMNNAME_A_Disposed_Method, method);
			}
		}
		
		asset.saveEx(get_TrxName());
		
		//	User Validation
		valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}
		
		// Done
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}	//	completeIt
	
	@Override
	public boolean voidIt()
	{
		throw new AssetNotImplementedException("");
	}	//	voidIt
	
	@Override
	public boolean closeIt()
	{
		setDocAction(DOCACTION_None);
		return true;
	}	//	closeIt
	
	@Override
	public boolean reverseCorrectIt()
	{
		throw new AssetNotImplementedException("");
	}	//	reverseCorrectionIt
	
	@Override
	public boolean reverseAccrualIt()
	{
		throw new AssetNotImplementedException("");
	}
	
	@Override
	public boolean reActivateIt()
	{
		throw new AssetNotImplementedException("");
	}
		
	@Override
	public String getSummary()
	{
		return new StringBuilder()
				.append(getDocumentNo()).append("/").append(getDateDoc())
				.toString();
	}

	@Override
	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg
	
	@Override
	public int getDoc_User_ID()
	{
		return getCreatedBy();
	}

	@Override
	public BigDecimal getApprovalAmt()
	{
		return Env.ZERO;
	} 
	
	@Override
	public int getC_Currency_ID()
	{
		return MClient.get(getCtx(), getAD_Client_ID()).getAcctSchema().getC_Currency_ID();
	}
	
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		if (getDateAcct() == null)
		{
			setDateAcct(getDateDoc());
		}
		if (newRecord || is_ValueChanged(COLUMNNAME_DateAcct))
		{
			setC_Period_ID(MPeriod.get(getCtx(), getDateAcct(), getAD_Org_ID(), get_TrxName()).get_ID());
		}
		if (getA_Disposed_Date() == null)
		{
			setA_Disposed_Date(getDateAcct());
		}
		return true;
	}
	
	/**
	 * Copy fields from A_Asset
	 * @param bean
	 */
	public static void updateFromAsset(I_A_Asset_Disposed bean)
	{
		int asset_id = bean.getA_Asset_ID();
		SetGetUtil.copyValues(
				SetGetUtil.wrap(bean),
				MAsset.Table_Name, asset_id,
				new String[] {
					MAsset.COLUMNNAME_IsDisposed,
					MAsset.COLUMNNAME_A_Asset_Status,
					"AD_Org_ID",
				}
		);
		
		String trxName = (bean instanceof PO) ? ((PO) bean).get_TrxName() : null;
		MDepreciationWorkfile wk = MDepreciationWorkfile.get(Env.getCtx(), asset_id, bean.getPostingType(), trxName);
		if (wk != null)
		{
			bean.setA_Asset_Cost(wk.getA_Asset_Cost());
			bean.setA_Accumulated_Depr(wk.getA_Accumulated_Depr());
		}
		else
		{
			bean.setA_Asset_Cost(Env.ZERO);
			bean.setA_Accumulated_Depr(Env.ZERO);
		}
	}
	
	@Override
	public File createPDF ()
	{
		return null;
	}	//	createPDF
	
	@Override
	public String getDocumentInfo()
	{
		return getDocumentNo();
	}	//	getDocumentInfo
	
	/**
	 * Check if this is a disposal (if the asset is not disposed)
	 * @return true if asset has been disposed
	 */
	public boolean isDisposal()
	{
		return !isDisposed();
	}
	
	/**
	 * Set asset disposal amount
	 * @param bean
	 */
	public static void setA_Disposal_Amt(I_A_Asset_Disposed bean)
	{
		int precision = 2;
		BigDecimal A_Asset_Cost = bean.getA_Asset_Cost();
		BigDecimal A_Disposal_Amt = bean.getA_Disposal_Amt();
		BigDecimal coef = Env.ZERO;
		if (A_Asset_Cost.signum() != 0)
		{
			coef = A_Disposal_Amt.divide(A_Asset_Cost, 12, RoundingMode.HALF_UP);
		}
		//
		BigDecimal A_Accumulated_Depr = bean.getA_Accumulated_Depr();
		BigDecimal A_Accumulated_Depr_Delta = A_Accumulated_Depr.multiply(coef).setScale(precision, RoundingMode.HALF_UP);
		BigDecimal Expense = A_Disposal_Amt.subtract(A_Accumulated_Depr_Delta);
		//
		bean.setA_Accumulated_Depr_Delta(A_Accumulated_Depr_Delta);
		bean.setExpense(Expense);
	}
	
	/**
	 * Process disposal
	 */
	private void createDisposal()
	{
		Map<Integer, BigDecimal> origCostMap = new HashMap<>();
		Map<Integer, BigDecimal> origSalvageMap = new HashMap<>();
		Map<Integer, BigDecimal> origAccumDeprMap = new HashMap<>();
		for (MDepreciationWorkfile assetwk :  MDepreciationWorkfile.forA_Asset_ID(getCtx(), getA_Asset_ID(), get_TrxName()))
		{
			origCostMap.put(assetwk.getC_AcctSchema_ID(), assetwk.getA_Asset_Cost());
			origSalvageMap.put(assetwk.getC_AcctSchema_ID(), assetwk.getA_Salvage_Value() != null ? assetwk.getA_Salvage_Value() : Env.ZERO);
			origAccumDeprMap.put(assetwk.getC_AcctSchema_ID(), assetwk.getA_Accumulated_Depr() != null ? assetwk.getA_Accumulated_Depr() : Env.ZERO);
			
			BigDecimal disposalAmt = Env.ZERO;
			BigDecimal accumDeprAmt = Env.ZERO;
			BigDecimal bookValueAmt = Env.ZERO;
			if (A_DISPOSED_METHOD_Prorated.equals(getA_Disposed_Method()))
			{
				disposalAmt = assetwk.getA_Asset_Cost();
				accumDeprAmt = calculateProratedCumulativeDepreciation(assetwk, getA_Disposed_Date());
				bookValueAmt = disposalAmt.subtract(accumDeprAmt);
			}
			else if (assetwk.getC_AcctSchema().getC_Currency_ID() != getC_Currency_ID()) 
			{
				disposalAmt  =  assetwk.getA_Asset_Cost();
				accumDeprAmt = assetwk.getA_Accumulated_Depr();
				bookValueAmt = assetwk.getA_Asset_Remaining();
			} 
			else
			{
				disposalAmt = getA_Disposal_Amt();
				accumDeprAmt = getA_Accumulated_Depr_Delta();
				bookValueAmt = assetwk.getA_Asset_Remaining();
			}			
			
			MAssetChange change = new MAssetChange (getCtx(), 0, get_TrxName());
			change.setAD_Org_ID(getAD_Org_ID()); 
			change.setA_Asset_ID(getA_Asset_ID());
			change.setChangeType(MAssetChange.CHANGETYPE_Disposal);
			change.setTextDetails(MRefList.getListDescription (getCtx(),"A_Update_Type" , MAssetChange.CHANGETYPE_Disposal));
			change.setPostingType(assetwk.getPostingType());
			change.setAssetValueAmt(disposalAmt);
			change.setAssetBookValueAmt(bookValueAmt);
			change.setAssetAccumDepreciationAmt(accumDeprAmt);
			change.setA_QTY_Current(assetwk.getA_QTY_Current());
			change.setC_AcctSchema_ID(assetwk.getC_AcctSchema_ID());
			change.setAssetDisposalDate(getA_Disposed_Date());
			change.setIsDisposed(true);
			change.saveEx(get_TrxName());
			
			assetwk.adjustCost(disposalAmt.negate(), Env.ZERO, false);
			BigDecimal deltaAccumDepr = A_DISPOSED_METHOD_Prorated.equals(getA_Disposed_Method())
					? assetwk.getA_Accumulated_Depr().negate()
					: accumDeprAmt.negate();
			assetwk.adjustAccumulatedDepr(deltaAccumDepr, assetwk.getA_Accumulated_Depr_F().negate(), false);
			assetwk.saveEx();
			assetwk.buildDepreciation();
		}
		//
		// Delete not processed expense entries
		List<MDepreciationExp> list = MDepreciationExp.getNotProcessedEntries(getCtx(), getA_Asset_ID(), getPostingType(), get_TrxName());
		if (A_DISPOSED_METHOD_Prorated.equals(getA_Disposed_Method()))
		{
			Timestamp disposedDate = getA_Disposed_Date();
			if (disposedDate == null)
			{
				disposedDate = getDateAcct();
			}
			if (disposedDate == null)
			{
				disposedDate = new Timestamp(System.currentTimeMillis());
			}
			LocalDate disposalLocalDate = disposedDate.toLocalDateTime().toLocalDate();
			
			// Determine accounting period of DateAcct to detect Forward Calculation
			Timestamp acctDate = getDateAcct();
			if (acctDate == null)
			{
				acctDate = Env.getContextAsDate(getCtx(), "#Date");
			}
			if (acctDate == null)
			{
				acctDate = new Timestamp(System.currentTimeMillis());
			}
			LocalDate acctLocalDate = acctDate.toLocalDateTime().toLocalDate();
			LocalDate acctPeriodEnd = acctLocalDate.with(TemporalAdjusters.lastDayOfMonth());
			MPeriod acctPeriod = MPeriod.get(getCtx(), acctDate, getAD_Org_ID(), get_TrxName());
			if (acctPeriod != null && acctPeriod.getEndDate() != null)
			{
				acctPeriodEnd = acctPeriod.getEndDate().toLocalDateTime().toLocalDate();
			}
			
			boolean isForward = disposalLocalDate.isAfter(acctPeriodEnd);
			
			// Determine period of A_Disposed_Date
			LocalDate disposalPeriodStart = disposalLocalDate.with(TemporalAdjusters.firstDayOfMonth());
			LocalDate disposalPeriodEnd = disposalLocalDate.with(TemporalAdjusters.lastDayOfMonth());
			MPeriod disposalPeriod = MPeriod.get(getCtx(), disposedDate, getAD_Org_ID(), get_TrxName());
			if (disposalPeriod != null && disposalPeriod.getStartDate() != null && disposalPeriod.getEndDate() != null)
			{
				disposalPeriodStart = disposalPeriod.getStartDate().toLocalDateTime().toLocalDate();
				disposalPeriodEnd = disposalPeriod.getEndDate().toLocalDateTime().toLocalDate();
			}
			
			for (MDepreciationExp ex : list)
			{
				Timestamp exDate = ex.getDateAcct();
				if (exDate == null)
				{
					ex.deleteEx(false);
					continue;
				}
				LocalDate exLocalDate = exDate.toLocalDateTime().toLocalDate();
				boolean isInDisposalPeriod = !exLocalDate.isBefore(disposalPeriodStart) && !exLocalDate.isAfter(disposalPeriodEnd);
				
				if (isForward && isInDisposalPeriod)
				{
					// Adjust value and end date of MDepreciationExp in the same period as A_Disposed_Date
					// Formula: nguyên giá trước dispose - salvage (slave) - Disposed (accumulated depreciation)
					BigDecimal origCost = origCostMap.get(ex.getC_AcctSchema_ID());
					if (origCost == null)
					{
						origCost = ex.getA_Asset_Cost() != null ? ex.getA_Asset_Cost() : Env.ZERO;
					}
					BigDecimal salvage = origSalvageMap.get(ex.getC_AcctSchema_ID());
					if (salvage == null)
					{
						salvage = Env.ZERO;
					}
					BigDecimal disposed = origAccumDeprMap.get(ex.getC_AcctSchema_ID());
					if (disposed == null)
					{
						disposed = ex.getA_Accumulated_Depr() != null ? ex.getA_Accumulated_Depr() : Env.ZERO;
					}
					
					// Add any intermediate unposted depreciation expenses before the disposal period
					for (MDepreciationExp priorEx : list)
					{
						if (priorEx.getC_AcctSchema_ID() == ex.getC_AcctSchema_ID() 
								&& priorEx.getA_Depreciation_Exp_ID() != ex.getA_Depreciation_Exp_ID())
						{
							Timestamp pDate = priorEx.getDateAcct();
							if (pDate != null && pDate.toLocalDateTime().toLocalDate().isBefore(disposalPeriodStart))
							{
								if (priorEx.getExpense() != null && priorEx.getExpense().signum() > 0)
								{
									disposed = disposed.add(priorEx.getExpense());
								}
							}
						}
					}
					
					BigDecimal newExpense = origCost.subtract(salvage).subtract(disposed);
					if (newExpense.signum() < 0)
					{
						newExpense = Env.ZERO;
					}
					
					MAcctSchema as = MAcctSchema.get(getCtx(), ex.getC_AcctSchema_ID());
					if (as != null)
					{
						newExpense = newExpense.setScale(as.getStdPrecision(), RoundingMode.HALF_UP);
					}
					
					ex.setExpense(newExpense);
					ex.setA_Accumulated_Depr(disposed.add(newExpense));
					ex.setDateAcct(disposedDate);
					ex.saveEx(get_TrxName());
				}
				else if (exLocalDate.isAfter(disposalLocalDate) || exLocalDate.isAfter(disposalPeriodEnd) || (!isForward && isInDisposalPeriod))
				{
					// Delete expense entries after A_Disposed_Date
					ex.deleteEx(false);
				}
			}
		}
		else
		{
			for (MDepreciationExp ex : list)
			{
				ex.deleteEx(false);
			}
		}
	}

	/**
	 * Rate information container for daily and monthly depreciation rates.
	 */
	private static class RateInfo
	{
		final BigDecimal dailyRate;
		final BigDecimal monthlyRate;

		RateInfo(BigDecimal dailyRate, BigDecimal monthlyRate)
		{
			this.dailyRate = dailyRate;
			this.monthlyRate = monthlyRate;
		}
	}

	/**
	 * Calculate prorated cumulative depreciation up to effective disposal date by days.
	 * 
	 * Uses ZZ_DailyRate / ZZMonthlyRate to calculate exact daily depreciation.
	 * 
	 * Disposal classification relative to the accounting period of DateAcct (acctDate):
	 * - Normal Disposal: A_Disposed_Date is between acct period start and end date.
	 * - Backward Calculation: A_Disposed_Date is before acct period start.
	 * - Forward Calculation: A_Disposed_Date is after acct period end.
	 * 
	 * @param assetwk depreciation workfile
	 * @param effectiveDisposalDate effective disposal date (A_Disposed_Date)
	 * @return required cumulative depreciation up to disposal date
	 */
	public BigDecimal calculateProratedCumulativeDepreciation(MDepreciationWorkfile assetwk, Timestamp effectiveDisposalDate)
	{
		if (effectiveDisposalDate == null)
		{
			effectiveDisposalDate = getDateAcct();
		}
		if (effectiveDisposalDate == null)
		{
			effectiveDisposalDate = new Timestamp(System.currentTimeMillis());
		}
		
		LocalDate disposalDate = effectiveDisposalDate.toLocalDateTime().toLocalDate();
		
		BigDecimal currentPostedDepr = assetwk.getA_Accumulated_Depr();
		if (currentPostedDepr == null)
		{
			currentPostedDepr = Env.ZERO;
		}
		
		int scale = 2;
		MAcctSchema as = MAcctSchema.get(getCtx(), assetwk.getC_AcctSchema_ID());
		if (as != null)
		{
			scale = as.getStdPrecision();
		}
		
		// Determine accounting period based on DateAcct (acctDate)
		Timestamp acctDate = getDateAcct();
		if (acctDate == null)
		{
			acctDate = Env.getContextAsDate(getCtx(), "#Date");
		}
		if (acctDate == null)
		{
			acctDate = new Timestamp(System.currentTimeMillis());
		}
		
		LocalDate acctLocalDate = acctDate.toLocalDateTime().toLocalDate();
		LocalDate acctPeriodStart = acctLocalDate.with(TemporalAdjusters.firstDayOfMonth());
		LocalDate acctPeriodEnd = acctLocalDate.with(TemporalAdjusters.lastDayOfMonth());
		
		MPeriod acctPeriod = MPeriod.get(getCtx(), acctDate, getAD_Org_ID(), get_TrxName());
		if (acctPeriod != null && acctPeriod.getStartDate() != null && acctPeriod.getEndDate() != null)
		{
			acctPeriodStart = acctPeriod.getStartDate().toLocalDateTime().toLocalDate();
			acctPeriodEnd = acctPeriod.getEndDate().toLocalDateTime().toLocalDate();
		}
		
		if (log.isLoggable(Level.FINE))
		{
			if (disposalDate.isBefore(acctPeriodStart))
			{
				log.fine("Prorated Disposal: Backward calculation (disposalDate " + disposalDate + " < acctPeriodStart " + acctPeriodStart + ", acctDate=" + acctLocalDate + ")");
			}
			else if (disposalDate.isAfter(acctPeriodEnd))
			{
				log.fine("Prorated Disposal: Forward calculation (disposalDate " + disposalDate + " > acctPeriodEnd " + acctPeriodEnd + ", acctDate=" + acctLocalDate + ")");
			}
			else
			{
				log.fine("Prorated Disposal: Normal calculation (disposalDate " + disposalDate + " in acct period [" + acctPeriodStart + ", " + acctPeriodEnd + "], acctDate=" + acctLocalDate + ")");
			}
		}
		
		MDepreciationExp lastExp = getLastProcessedDepreciationExp(getCtx(), assetwk.getA_Asset_ID(),
				assetwk.getPostingType(), assetwk.getC_AcctSchema_ID(), get_TrxName());
		
		LocalDate lastPostedDate = null;
		if (lastExp != null && lastExp.getDateAcct() != null)
		{
			lastPostedDate = lastExp.getDateAcct().toLocalDateTime().toLocalDate().with(TemporalAdjusters.lastDayOfMonth());
		}
		
		if (lastPostedDate != null && !disposalDate.isAfter(lastPostedDate))
		{
			// Depreciation has been posted up to or beyond disposalDate -> Reverse excess
			return calculateBackwardDailyDepreciation(assetwk, disposalDate, currentPostedDepr, scale);
		}
		else
		{
			// Depreciation has NOT reached disposalDate -> Calculate forward / outstanding
			return calculateForwardDailyDepreciation(assetwk, lastExp, lastPostedDate, disposalDate, currentPostedDepr, scale);
		}
	}

	/**
	 * Extract daily and monthly depreciation rate from A_Depreciation_Exp.
	 * Checks ZZ_DailyRate, ZZMonthlyRate on exp (or any other entry for this asset),
	 * then derives using averageDaysInMonth (365/12).
	 * If rates cannot be queried, throws AdempiereException and stops processing.
	 */
	private RateInfo getDailyAndMonthlyRate(MDepreciationWorkfile assetwk, MDepreciationExp exp, int scale)
	{
		BigDecimal dailyRate = null;
		BigDecimal monthlyRate = null;
		
		if (exp != null)
		{
			Object zzDailyRate = exp.get_Value("ZZ_DailyRate");
			if (zzDailyRate instanceof BigDecimal && ((BigDecimal) zzDailyRate).signum() > 0)
			{
				dailyRate = (BigDecimal) zzDailyRate;
			}
			Object zzMonthlyRate = exp.get_Value("ZZMonthlyRate");
			if (zzMonthlyRate instanceof BigDecimal && ((BigDecimal) zzMonthlyRate).signum() > 0)
			{
				monthlyRate = (BigDecimal) zzMonthlyRate;
			}
			if (monthlyRate == null && exp.getExpense() != null && exp.getExpense().signum() > 0)
			{
				monthlyRate = exp.getExpense();
			}
		}
		
		// If not found in exp, search across all A_Depreciation_Exp entries for this asset
		if (dailyRate == null && monthlyRate == null)
		{
			MDepreciationExp anyExp = getAnyDepreciationExpWithRates(assetwk);
			if (anyExp != null)
			{
				Object zzDailyRate = anyExp.get_Value("ZZ_DailyRate");
				if (zzDailyRate instanceof BigDecimal && ((BigDecimal) zzDailyRate).signum() > 0)
				{
					dailyRate = (BigDecimal) zzDailyRate;
				}
				Object zzMonthlyRate = anyExp.get_Value("ZZMonthlyRate");
				if (zzMonthlyRate instanceof BigDecimal && ((BigDecimal) zzMonthlyRate).signum() > 0)
				{
					monthlyRate = (BigDecimal) zzMonthlyRate;
				}
				if (monthlyRate == null && anyExp.getExpense() != null && anyExp.getExpense().signum() > 0)
				{
					monthlyRate = anyExp.getExpense();
				}
			}
		}
		
		BigDecimal averageDaysInMonth = new BigDecimal("365").divide(new BigDecimal("12"), MathContext.DECIMAL128);
		
		if (dailyRate != null && monthlyRate == null)
		{
			monthlyRate = dailyRate.multiply(averageDaysInMonth).setScale(scale, RoundingMode.HALF_UP);
		}
		else if (monthlyRate != null && dailyRate == null)
		{
			dailyRate = monthlyRate.divide(averageDaysInMonth, MathContext.DECIMAL128);
		}
		
		// If dailyRate or monthlyRate could not be queried, throw exception and stop processing
		if (dailyRate == null || monthlyRate == null || dailyRate.signum() <= 0 || monthlyRate.signum() <= 0)
		{
			throw new AdempiereException("Cannot query dailyRate / monthlyRate (ZZ_DailyRate / ZZMonthlyRate) for asset (ID=" 
					+ assetwk.getA_Asset_ID() + ", PostingType=" + assetwk.getPostingType() + "). Processing stopped.");
		}
		
		return new RateInfo(dailyRate, monthlyRate);
	}

	/**
	 * Search for any A_Depreciation_Exp entry with ZZ_DailyRate or ZZMonthlyRate for this asset
	 */
	private MDepreciationExp getAnyDepreciationExpWithRates(MDepreciationWorkfile assetwk)
	{
		String whereClause = MDepreciationExp.COLUMNNAME_A_Asset_ID + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_PostingType + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_C_AcctSchema_ID + " = ?"
				+ " AND IsActive = 'Y'";
		List<MDepreciationExp> list = new Query(getCtx(), MDepreciationExp.Table_Name, whereClause, get_TrxName())
				.setParameters(assetwk.getA_Asset_ID(), assetwk.getPostingType(), assetwk.getC_AcctSchema_ID())
				.setOrderBy(MDepreciationExp.COLUMNNAME_DateAcct + " DESC, " + MDepreciationExp.COLUMNNAME_A_Period + " DESC")
				.list();
		for (MDepreciationExp e : list)
		{
			Object val = e.get_Value("ZZ_DailyRate");
			if (val instanceof BigDecimal && ((BigDecimal) val).signum() > 0)
			{
				return e;
			}
			val = e.get_Value("ZZMonthlyRate");
			if (val instanceof BigDecimal && ((BigDecimal) val).signum() > 0)
			{
				return e;
			}
		}
		// Fallback: search across all schemas for this asset
		whereClause = MDepreciationExp.COLUMNNAME_A_Asset_ID + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_PostingType + " = ?"
				+ " AND IsActive = 'Y'";
		list = new Query(getCtx(), MDepreciationExp.Table_Name, whereClause, get_TrxName())
				.setParameters(assetwk.getA_Asset_ID(), assetwk.getPostingType())
				.setOrderBy(MDepreciationExp.COLUMNNAME_DateAcct + " DESC, " + MDepreciationExp.COLUMNNAME_A_Period + " DESC")
				.list();
		for (MDepreciationExp e : list)
		{
			Object val = e.get_Value("ZZ_DailyRate");
			if (val instanceof BigDecimal && ((BigDecimal) val).signum() > 0)
			{
				return e;
			}
			val = e.get_Value("ZZMonthlyRate");
			if (val instanceof BigDecimal && ((BigDecimal) val).signum() > 0)
			{
				return e;
			}
		}
		return null;
	}

	/**
	 * Verify that prorated disposal is allowed for this asset.
	 * Checks ZZDisposedProrated on MAssetAcct, then falls back to MAssetGroupAcct.
	 * Allowed values: 'Y' (allowed), 'N' (disallowed), null (inherit from group).
	 * If 'N', throws AdempiereException and stops processing.
	 * If not 'Y', throws AdempiereException and stops processing.
	 */
	private void checkDisposedProratedAllowed()
	{
		for (MDepreciationWorkfile wk : MDepreciationWorkfile.forA_Asset_ID(getCtx(), getA_Asset_ID(), get_TrxName()))
		{
			MAssetAcct assetAcct = MAssetAcct.forA_Asset_ID(getCtx(), wk.getC_AcctSchema_ID(), getA_Asset_ID(), wk.getPostingType(), getDateAcct(), get_TrxName());
			String prorated = null;
			if (assetAcct != null)
			{
				prorated = assetAcct.getZZDisposedProrated();
			}
			if (prorated == null)
			{
				MAsset asset = getAsset();
				if (asset != null && asset.getA_Asset_Group_ID() > 0)
				{
					MAssetGroupAcct grpAcct = MAssetGroupAcct.forA_Asset_Group_ID(getCtx(), asset.getA_Asset_Group_ID(), wk.getPostingType(), wk.getC_AcctSchema_ID());
					if (grpAcct != null)
					{
						prorated = grpAcct.getZZDisposedProrated();
					}
				}
			}
			
			if (X_A_Asset_Acct.ZZDISPOSEDPRORATED_No.equals(prorated))
			{
				throw new AdempiereException("Asset (ID=" + getA_Asset_ID() 
						+ ", AcctSchema=" + wk.getC_AcctSchema_ID() 
						+ ") is configured not to allow prorated disposal (ZZDisposedProrated='N'). Processing stopped.");
			}
			else if (!X_A_Asset_Acct.ZZDISPOSEDPRORATED_Yes.equals(prorated))
			{
				throw new AdempiereException("Asset (ID=" + getA_Asset_ID() 
						+ ", AcctSchema=" + wk.getC_AcctSchema_ID() 
						+ ") is not enabled for prorated disposal (ZZDisposedProrated is not 'Y'). Processing stopped.");
			}
		}
	}

	/**
	 * Verify that ZZDisposalProratedAdj_Acct is defined for all accounting schemas of this asset.
	 * Checks MAssetAcct, then falls back to MAssetGroupAcct.
	 * If not defined, throws AdempiereException and stops processing.
	 */
	private void checkDepreciationAdjustmentAccount()
	{
		for (MDepreciationWorkfile wk : MDepreciationWorkfile.forA_Asset_ID(getCtx(), getA_Asset_ID(), get_TrxName()))
		{
			MAssetAcct assetAcct = MAssetAcct.forA_Asset_ID(getCtx(), wk.getC_AcctSchema_ID(), getA_Asset_ID(), wk.getPostingType(), getDateAcct(), get_TrxName());
			int acctId = 0;
			if (assetAcct != null)
			{
				acctId = assetAcct.getZZDisposalProratedAdj_Acct();
			}
			if (acctId <= 0)
			{
				MAsset asset = getAsset();
				if (asset != null && asset.getA_Asset_Group_ID() > 0)
				{
					MAssetGroupAcct grpAcct = MAssetGroupAcct.forA_Asset_Group_ID(getCtx(), asset.getA_Asset_Group_ID(), wk.getPostingType(), wk.getC_AcctSchema_ID());
					if (grpAcct != null)
					{
						acctId = grpAcct.getZZDisposalProratedAdj_Acct();
					}
				}
			}
			if (acctId <= 0)
			{
				throw new AdempiereException("Account " + I_A_Asset_Acct.COLUMNNAME_ZZDisposalProratedAdj_Acct 
						+ " is not defined for asset (ID=" + getA_Asset_ID() 
						+ ", AcctSchema=" + wk.getC_AcctSchema_ID() + "). Processing stopped.");
			}
		}
	}

	/**
	 * Calculate required cumulative depreciation for Backward calculation (by days).
	 * Determines excess depreciation posted after disposalDate and subtracts from currentPostedDepr.
	 */
	private BigDecimal calculateBackwardDailyDepreciation(MDepreciationWorkfile assetwk, LocalDate disposalDate,
			BigDecimal currentPostedDepr, int scale)
	{
		LocalDate disposalMonthStart = disposalDate.with(TemporalAdjusters.firstDayOfMonth());
		LocalDate disposalMonthEnd = disposalDate.with(TemporalAdjusters.lastDayOfMonth());
		
		List<MDepreciationExp> processedEntries = getProcessedDepreciationExpFrom(assetwk, disposalMonthStart);
		BigDecimal totalExcess = Env.ZERO;
		
		BigDecimal postedDisposalMonthExpense = Env.ZERO;
		MDepreciationExp lastEntryInDisposalMonth = null;
		
		for (MDepreciationExp exp : processedEntries)
		{
			if (exp.getDateAcct() == null || exp.getExpense() == null || exp.getExpense().signum() <= 0)
			{
				continue;
			}
			
			LocalDate expDate = exp.getDateAcct().toLocalDateTime().toLocalDate();
			LocalDate expMonthStart = expDate.with(TemporalAdjusters.firstDayOfMonth());
			
			if (expMonthStart.equals(disposalMonthStart))
			{
				postedDisposalMonthExpense = postedDisposalMonthExpense.add(exp.getExpense());
				lastEntryInDisposalMonth = exp;
			}
			else if (expMonthStart.isAfter(disposalMonthStart))
			{
				// Entire subsequent month is excess
				totalExcess = totalExcess.add(exp.getExpense());
			}
		}
		
		if (postedDisposalMonthExpense.signum() > 0)
		{
			LocalDate startDay = disposalMonthStart;
			Timestamp serviceDateTS = assetwk.getAssetServiceDate();
			if (serviceDateTS != null)
			{
				LocalDate inService = serviceDateTS.toLocalDateTime().toLocalDate();
				if (inService.isAfter(startDay))
				{
					startDay = inService;
				}
			}
			
			long activeDays = ChronoUnit.DAYS.between(startDay, disposalDate) + 1;
			if (activeDays <= 0)
			{
				totalExcess = totalExcess.add(postedDisposalMonthExpense);
			}
			else if (disposalDate.equals(disposalMonthEnd) && startDay.getDayOfMonth() == 1)
			{
				// Full month was valid, no excess in disposal month
			}
			else
			{
				RateInfo rates = getDailyAndMonthlyRate(assetwk, lastEntryInDisposalMonth, scale);
				BigDecimal validMonthDepr = BigDecimal.valueOf(activeDays).multiply(rates.dailyRate).setScale(scale, RoundingMode.HALF_UP);
				if (validMonthDepr.compareTo(postedDisposalMonthExpense) < 0)
				{
					totalExcess = totalExcess.add(postedDisposalMonthExpense.subtract(validMonthDepr));
				}
			}
		}
		
		BigDecimal requiredDepr = currentPostedDepr.subtract(totalExcess);
		if (requiredDepr.signum() < 0)
		{
			requiredDepr = Env.ZERO;
		}
		return requiredDepr;
	}

	/**
	 * Get processed depreciation entries starting from a given month
	 */
	private List<MDepreciationExp> getProcessedDepreciationExpFrom(MDepreciationWorkfile assetwk, LocalDate fromDate)
	{
		String whereClause = MDepreciationExp.COLUMNNAME_A_Asset_ID + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_PostingType + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_C_AcctSchema_ID + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_Processed + " = 'Y'"
				+ " AND IsActive = 'Y'"
				+ " AND " + MDepreciationExp.COLUMNNAME_DateAcct + " >= ?";
		
		Timestamp fromTS = Timestamp.valueOf(fromDate.atStartOfDay());
		return new Query(getCtx(), MDepreciationExp.Table_Name, whereClause, get_TrxName())
				.setParameters(assetwk.getA_Asset_ID(), assetwk.getPostingType(), assetwk.getC_AcctSchema_ID(), fromTS)
				.setOrderBy(MDepreciationExp.COLUMNNAME_DateAcct + " ASC")
				.list();
	}

	/**
	 * Calculate forward cumulative depreciation by days up to disposalDate.
	 */
	private BigDecimal calculateForwardDailyDepreciation(MDepreciationWorkfile assetwk, MDepreciationExp lastExp,
			LocalDate lastPostedDate, LocalDate disposalDate, BigDecimal currentPostedDepr, int scale)
	{
		BigDecimal maxDepr = assetwk.getA_Asset_Cost();
		if (assetwk.getA_Salvage_Value() != null)
		{
			maxDepr = maxDepr.subtract(assetwk.getA_Salvage_Value());
		}
		if (maxDepr.signum() <= 0)
		{
			return currentPostedDepr;
		}
		
		// Determine start date for forward calculation
		LocalDate nextDate;
		if (lastPostedDate != null)
		{
			nextDate = lastPostedDate.plusDays(1);
		}
		else
		{
			// Asset has not been depreciated yet
			Timestamp serviceDate = assetwk.getAssetServiceDate();
			if (serviceDate == null)
			{
				serviceDate = assetwk.getDateAcct();
			}
			if (serviceDate != null)
			{
				LocalDate inService = serviceDate.toLocalDateTime().toLocalDate();
				if (inService.isAfter(disposalDate))
				{
					return Env.ZERO;
				}
				nextDate = inService;
			}
			else
			{
				nextDate = disposalDate.with(TemporalAdjusters.firstDayOfMonth());
			}
		}
		
		if (nextDate.isAfter(disposalDate))
		{
			return currentPostedDepr;
		}
		
		RateInfo baseRates = getDailyAndMonthlyRate(assetwk, lastExp, scale);
		BigDecimal accumDepr = currentPostedDepr;
		LocalDate curDate = nextDate;
		
		while (!curDate.isAfter(disposalDate))
		{
			BigDecimal remainingToDepr = maxDepr.subtract(accumDepr);
			if (remainingToDepr.signum() <= 0)
			{
				break;
			}
			
			LocalDate curMonthEnd = curDate.with(TemporalAdjusters.lastDayOfMonth());
			BigDecimal monthExpense = Env.ZERO;
			
			if (!disposalDate.isBefore(curMonthEnd) && curDate.getDayOfMonth() == 1)
			{
				// Full calendar month
				MDepreciationExp unpostedExp = getUnpostedDepreciationExp(assetwk, curMonthEnd);
				if (unpostedExp != null && unpostedExp.getExpense() != null && unpostedExp.getExpense().signum() > 0)
				{
					monthExpense = unpostedExp.getExpense();
				}
				else
				{
					monthExpense = baseRates.monthlyRate;
				}
				curDate = curMonthEnd.plusDays(1);
			}
			else
			{
				// Partial month (either first partial month, or disposal month)
				LocalDate rangeEnd = disposalDate.isBefore(curMonthEnd) ? disposalDate : curMonthEnd;
				long activeDays = ChronoUnit.DAYS.between(curDate, rangeEnd) + 1;
				if (activeDays > 0)
				{
					monthExpense = BigDecimal.valueOf(activeDays).multiply(baseRates.dailyRate).setScale(scale, RoundingMode.HALF_UP);
				}
				curDate = rangeEnd.plusDays(1);
			}
			
			if (monthExpense.compareTo(remainingToDepr) > 0)
			{
				monthExpense = remainingToDepr;
			}
			accumDepr = accumDepr.add(monthExpense);
		}
		
		return accumDepr;
	}

	/**
	 * Get last processed depreciation entry for the asset
	 */
	private MDepreciationExp getLastProcessedDepreciationExp(Properties ctx, int assetID, String postingType, int acctSchemaID, String trxName)
	{
		String whereClause = MDepreciationExp.COLUMNNAME_A_Asset_ID + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_PostingType + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_Processed + " = ?"
				+ " AND IsActive = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_C_AcctSchema_ID + " = ?";
		
		return new Query(ctx, MDepreciationExp.Table_Name, whereClause, trxName)
				.setParameters(assetID, postingType, true, true, acctSchemaID)
				.setOrderBy(MDepreciationExp.COLUMNNAME_DateAcct + " DESC, " + MDepreciationExp.COLUMNNAME_A_Period + " DESC")
				.first();
	}

	/**
	 * Get unposted depreciation entry for a specific month
	 */
	private MDepreciationExp getUnpostedDepreciationExp(MDepreciationWorkfile assetwk, LocalDate monthEnd)
	{
		String whereClause = MDepreciationExp.COLUMNNAME_A_Asset_ID + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_PostingType + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_C_AcctSchema_ID + " = ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_Processed + " = 'N'"
				+ " AND IsActive = 'Y'"
				+ " AND " + MDepreciationExp.COLUMNNAME_DateAcct + " >= ?"
				+ " AND " + MDepreciationExp.COLUMNNAME_DateAcct + " <= ?";
		
		Timestamp startOfMonth = Timestamp.valueOf(monthEnd.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay());
		Timestamp endOfMonth = Timestamp.valueOf(monthEnd.atTime(23, 59, 59));
		
		return new Query(getCtx(), MDepreciationExp.Table_Name, whereClause, get_TrxName())
				.setParameters(assetwk.getA_Asset_ID(), assetwk.getPostingType(), assetwk.getC_AcctSchema_ID(), startOfMonth, endOfMonth)
				.first();
	}
}
