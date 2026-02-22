/******************************************************************************
 * Product: Posterita Ajax UI 												  *
 * Copyright (C) 2007 Posterita Ltd.  All Rights Reserved.                    *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Posterita Ltd., 3, Draper Avenue, Quatre Bornes, Mauritius                 *
 * or via info@posterita.org or http://www.posterita.org/                     *
 *                                                                            *
 * Contributors:                                                              *
 * - Heng Sin Low                                                             *
 *                                                                            *
 * Sponsors:                                                                  *
 * - Idalica Corporation                                                      *
 *****************************************************************************/

package org.adempiere.webui.panel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.util.LogAuthFailure;
import org.adempiere.webui.AdempiereWebUI;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Combobox;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Textbox;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.event.TokenEvent;
import org.adempiere.webui.exception.ApplicationException;
import org.adempiere.webui.session.SessionManager;
import org.adempiere.webui.theme.ITheme;
import org.adempiere.webui.theme.ThemeManager;
import org.adempiere.webui.util.BrowserToken;
import org.adempiere.webui.util.UserPreference;
import org.adempiere.webui.window.Dialog;
import org.adempiere.webui.window.LoginWindow;
import org.compiere.Adempiere;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MClient;
import org.compiere.model.MSession;
import org.compiere.model.MSysConfig;
import org.compiere.model.MSystem;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Language;
import org.compiere.util.Login;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.compiere.util.WebUtil;
import org.zkoss.lang.Strings;
import org.zkoss.util.Locales;
import org.zkoss.util.media.AMedia;
import org.zkoss.web.Attributes;
import org.zkoss.zhtml.Div;
import org.zkoss.zhtml.Form;
import org.zkoss.zhtml.Table;
import org.zkoss.zhtml.Td;
import org.zkoss.zhtml.Tr;
import org.zkoss.zk.au.out.AuFocus;
import org.zkoss.zk.au.out.AuScript;
import org.zkoss.zk.ui.AbstractComponent;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.sys.ComponentCtrl;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.A;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Image;
import org.zkoss.zul.Vbox;

import za.ntier.models.X_ZZ_Open_Application;

/**
 * Login panel of {@link LoginWindow}
 * @author  <a href="mailto:agramdass@gmail.com">Ashley G Ramdass</a>
 * @date    Feb 25, 2007
 * @author <a href="mailto:sendy.yagambrum@posterita.org">Sendy Yagambrum</a>
 * @date    July 18, 2007
 */
public class LoginPanel extends Window implements EventListener<Event>
{
	/**
	 * generated serial id
	 */
	private static final long serialVersionUID = -7859522563172088496L;

	public static final String ROLE_TYPES_WEBUI = "NULL,ZK,SS";  //webui,support+null

	private static LogAuthFailure logAuthFailure = new LogAuthFailure();

	private static final String ON_LOAD_TOKEN = "onLoadToken";
    private static final CLogger logger = CLogger.getCLogger(LoginPanel.class);

    protected Properties ctx;
    protected Label lblUserId;
    protected Label lblPassword;
    protected Label lblLanguage;
    protected Label lblLogin;    
    protected Textbox txtUserId;
    protected Textbox txtPassword;
    protected Combobox lstLanguage;
    protected LoginWindow wndLogin;
    protected Checkbox chkRememberMe;
    protected Checkbox chkSelectRole;
    protected A btnResetPassword;
    protected ConfirmPanel pnlButtons; 
    protected boolean email_login = MSysConfig.getBooleanValue(MSysConfig.USE_EMAIL_FOR_LOGIN, false);
    protected String validLstLanguage = null;
    // Martin 12/08/2025
    protected A btnRegister = null;
 // NEW: Additional link shown below "Register User"
    protected A btnRegisterSdf = null;
    
 // MQA Terms & Conditions controls
    protected Checkbox chkAcceptTerms;
    protected Checkbox chkDeclineTerms;
    protected A lnkTerms;
    protected A lnkTrainingManual;

    



	/* Number of failures to calculate an incremental delay on every trial */
	private int failures = 0;
    
	/**
	 * @param ctx
	 * @param loginWindow
	 */
    public LoginPanel(Properties ctx, LoginWindow loginWindow)
    {
        this.ctx = ctx;
        this.wndLogin = loginWindow;
        initComponents();
        init();
        this.setId("loginPanel");
        this.setSclass("login-box");
		if (isLabelAboveInput())
			LayoutUtils.addSclass("form-label-above-input", this);

        txtUserId.setEnabled(false);
        txtPassword.setEnabled(false);
        lstLanguage.setEnabled(false);
        Events.echoEvent(ON_LOAD_TOKEN, this, null);
        this.addEventListener(ON_LOAD_TOKEN, this);
    }

    /**
     * Layout panel
     */
    private void init()
    {
    	createUI();

        txtUserId.addEventListener(TokenEvent.ON_USER_TOKEN, new EventListener<Event>() {

			@Override
			public void onEvent(Event event) throws Exception {
				String[] data = (String[]) event.getData();
				try
				{
					int AD_Session_ID = Integer.parseInt(data[0]);
					int cnt = DB.getSQLValueEx(null, "SELECT COUNT(*) FROM AD_Session WHERE AD_Session_ID=?", AD_Session_ID);
					if (cnt == 1)
					{
						MSession session = new MSession(Env.getCtx(), AD_Session_ID, null);
						if (session.get_ID() == AD_Session_ID)
						{
							int AD_User_ID = session.getCreatedBy();
							MUser user = MUser.get(Env.getCtx(), AD_User_ID);
							if (user != null && user.get_ID() == AD_User_ID)
							{
							    String token = data[1];
							    if (BrowserToken.validateToken(session, user, token))
							    {
							    	onUserIdChange(AD_User_ID);
							    	if (MSystem.isZKRememberUserAllowed()) {
							    		String fillUser = null;
						    			if (user.getLDAPUser() != null && user.getLDAPUser().length() > 0) {
						    				fillUser = user.getLDAPUser();
						    			} else if (email_login){
						    				fillUser = user.getEMail();
						    			}else {
						    				fillUser = user.getName();
						    			}
							    		if (MSystem.isUseLoginPrefix()) {
							    			MClient client = MClient.get(session.getAD_Client_ID());
							    			if (! Util.isEmpty(client.getLoginPrefix())) {
								    			String separator = MSysConfig.getValue(MSysConfig.LOGIN_PREFIX_SEPARATOR, "/");
								    			fillUser = client.getLoginPrefix() + separator + fillUser;
							    			}
							    		}
						    			txtUserId.setValue(fillUser);
								    	chkRememberMe.setChecked(true);
							    	}
							    	if (MSystem.isZKRememberPasswordAllowed()) {
								    	txtPassword.setValue(token);
								    	txtPassword.setAttribute("user.token.hash", token);
								    	txtPassword.setAttribute("user.token.sid", AD_Session_ID);
							    	}
							    	chkSelectRole.setChecked(false);
							    }
							}
						}
					}
				} catch (Exception e) {
					//safe to ignore
					if (logger.isLoggable(Level.INFO))logger.log(Level.INFO, e.getLocalizedMessage(), e);
				}
			}
		});

        // Make the default language the browser language; otherwise it will be the language of client System
        List<String> browserLanguages = browserLanguages(Executions.getCurrent().getHeader("accept-language"));
        String defaultSystemLanguage = MClient.get(ctx, 0).getAD_Language();
        if (!browserLanguages.contains(defaultSystemLanguage))
        	browserLanguages.add(defaultSystemLanguage);
        boolean found = false;
        for (String browserLanguage : browserLanguages) {
            for (int i = 0; i < lstLanguage.getItemCount(); i++) {
            	Comboitem li = lstLanguage.getItemAtIndex(i);
            	String lang = li.getValue();
            	if (lang.startsWith(browserLanguage)) {
            		lstLanguage.setSelectedIndex(i);
            		languageChanged(li.getLabel());
            		found = true;
            		break;
            	}
            }
            if (found)
            	break;
        }
        
        txtUserId.removeEventListener(Events.ON_FOCUS, txtUserId);
        txtPassword.removeEventListener(Events.ON_FOCUS, txtPassword);
    }

	private boolean isLabelAboveInput() {
		return LayoutUtils.isLabelAboveInputForSmallWidth();
	}

    /**
     * Layout panel
     */
	
	protected void createUI() {

	    Form form = new Form();

	    // Header
	    Div div = new Div();
	    div.setSclass(ITheme.LOGIN_BOX_HEADER_CLASS);
	    lblLogin = new Label(Msg.getMsg(Env.getCtx(), "LoginHeader"));
	    lblLogin.setSclass(ITheme.LOGIN_BOX_HEADER_TXT_CLASS);
	    div.appendChild(lblLogin);
	    form.appendChild(div);

	    // Body table
	    Table table = new Table();
	    table.setId("grdLogin");
	    table.setDynamicProperty("cellpadding", "0");
	    table.setDynamicProperty("cellspacing", "5");
	    table.setSclass(ITheme.LOGIN_BOX_BODY_CLASS);
	    form.appendChild(table);

	    // Logo row
	    Tr tr = new Tr();
	    table.appendChild(tr);
	    Td td = new Td();
	    td.setSclass(ITheme.LOGIN_BOX_HEADER_LOGO_CLASS);
	    tr.appendChild(td);
	    td.setDynamicProperty("colspan", "2");
	    Image image = new Image();
	    image.setSrc(ThemeManager.getLargeLogo());
	    td.appendChild(image);

	    // User row
	    tr = new Tr();
	    tr.setId("rowUser");
	    table.appendChild(tr);
	    td = new Td();
	    tr.appendChild(td);
	    td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	    td.appendChild(lblUserId);
	    if (isLabelAboveInput()) {
	        tr = new Tr();
	        table.appendChild(tr);
	        td.setSclass(td.getSclass() + " form-label-above-input");
	    }
	    td = new Td();
	    td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	    tr.appendChild(td);
	    td.appendChild(txtUserId);

	    // Password row
	    tr = new Tr();
	    tr.setId("rowPassword");
	    table.appendChild(tr);
	    td = new Td();
	    tr.appendChild(td);
	    td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	    td.appendChild(lblPassword);
	    if (isLabelAboveInput()) {
	        tr = new Tr();
	        table.appendChild(tr);
	        td.setSclass(td.getSclass() + " form-label-above-input");
	    }
	    td = new Td();
	    td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	    tr.appendChild(td);
	    td.appendChild(txtPassword);

	    // Select Role row
	    tr = new Tr();
	    tr.setId("rowSelectRole");
	    table.appendChild(tr);
	    td = new Td();
	    tr.appendChild(td);
	    td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	    td.appendChild(new Label(""));
	    if (isLabelAboveInput()) {
	        tr = new Tr();
	        table.appendChild(tr);
	    }
	    td = new Td();
	    td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	    tr.appendChild(td);
	    td.appendChild(chkSelectRole);

	    // Remember Me row
	    if (MSystem.isZKRememberUserAllowed()) {
	        tr = new Tr();
	        tr.setId("rowRememberMe");
	        table.appendChild(tr);
	        td = new Td();
	        tr.appendChild(td);
	        td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	        td.appendChild(new Label(""));
	        if (isLabelAboveInput()) {
	            tr = new Tr();
	            table.appendChild(tr);
	        }
	        td = new Td();
	        td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	        tr.appendChild(td);
	        td.appendChild(chkRememberMe);
	    }

	    // Reset Password + Register links
	    if (MSysConfig.getBooleanValue(MSysConfig.LOGIN_SHOW_RESETPASSWORD, true)) {
	        tr = new Tr();
	        tr.setId("rowResetPassword");
	        table.appendChild(tr);
	        td = new Td();
	        tr.appendChild(td);
	        td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	        td.appendChild(new Label(""));
	        if (isLabelAboveInput()) {
	            tr = new Tr();
	            table.appendChild(tr);
	        }
	        td = new Td();
	        td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	        tr.appendChild(td);
	        td.appendChild(btnResetPassword);
	        btnResetPassword.addEventListener(Events.ON_CLICK, this);

	        tr = new Tr();
	        tr.setId("rowRegisterUser");
	        table.appendChild(tr);
	        td = new Td();
	        tr.appendChild(td);
	        td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	        td.appendChild(new Label(""));
	        if (isLabelAboveInput()) {
	            tr = new Tr();
	            table.appendChild(tr);
	        }
	        td = new Td();
	        td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	        tr.appendChild(td);
	        td.appendChild(btnRegister);
	        btnRegister.addEventListener(Events.ON_CLICK, this);

	        tr = new Tr();
	        tr.setId("rowRegisterUserSdf");
	        table.appendChild(tr);
	        td = new Td();
	        tr.appendChild(td);
	        td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	        td.appendChild(new Label(""));
	        if (isLabelAboveInput()) {
	            tr = new Tr();
	            table.appendChild(tr);
	        }
	        
	        td = new Td();
	        td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	        tr.appendChild(td);
	        td.appendChild(btnRegisterSdf);
	        btnRegisterSdf.addEventListener(Events.ON_CLICK, this);
	        
	    }

	    // ===== Terms & Conditions (Accept + link) =====
	    tr = new Tr();
	    tr.setId("rowTermsAccept");
	    table.appendChild(tr);

	    Td tdAcceptLbl = new Td();
	    tr.appendChild(tdAcceptLbl);
	    tdAcceptLbl.setSclass(ITheme.LOGIN_LABEL_CLASS);
	    tdAcceptLbl.appendChild(new Label(""));

	    if (isLabelAboveInput()) {
	        tr = new Tr();
	        table.appendChild(tr);
	    }

	    Td tdAccept = new Td();
	    tdAccept.setSclass(ITheme.LOGIN_FIELD_CLASS);
	    tr.appendChild(tdAccept);

	    Hbox acceptLine = new Hbox();
	    acceptLine.setSpacing("6px");
	    acceptLine.appendChild(chkAcceptTerms);
	    acceptLine.appendChild(new Label("I accept MQA"));
	    acceptLine.appendChild(lnkTerms);
	    tdAccept.appendChild(acceptLine);

	    lnkTerms.addEventListener(Events.ON_CLICK, ev -> openTermsAndConditions());
	    
	    
	    	
	    
	 // ===== DG Application Training Manual (only show if found) =====
	   // if (hasTrainingManual()) {

	        tr = new Tr();
	        tr.setId("rowTrainingManual");
	        table.appendChild(tr);

	        // label spacer keeps two-column alignment
	        td = new Td();
	        td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	        td.appendChild(new Label(""));
	        tr.appendChild(td);

	        if (isLabelAboveInput()) {
	            tr = new Tr();
	            table.appendChild(tr);
	        }

	        // field cell (same column as links, left aligned)
	        td = new Td();
	        td.setSclass(ITheme.LOGIN_FIELD_CLASS);
	        tr.appendChild(td);

	        td.appendChild(lnkTrainingManual);
	        lnkTrainingManual.addEventListener(Events.ON_CLICK, ev -> downloadTrainingManual());
	  //  }




	    // ===== OK + Tagline in the same (right-aligned) field cell =====
	    tr = new Tr();
	    tr.setId("rowOkAndTag");
	    table.appendChild(tr);

	    // Label spacer keeps two-column alignment
	    td = new Td();
	    td.setSclass(ITheme.LOGIN_LABEL_CLASS);
	    td.appendChild(new Label(""));
	    tr.appendChild(td);

	    // Field cell (right-aligned)
	    Td tdField = new Td();
	    tdField.setSclass(ITheme.LOGIN_FIELD_CLASS);
	    tdField.setDynamicProperty("align", "right"); // HTML align
	    tdField.setStyle("text-align:right;");        // CSS fallback
	    tr.appendChild(tdField);

	    // Inside that cell, left-align a small block so OK & tagline share left edge
	    Vbox stack = new Vbox();
	    stack.setSpacing("6px");
	    stack.setAlign("start");
	    stack.setStyle("display:inline-block; margin-right:16px;");

	    // ConfirmPanel / OK button
	    pnlButtons = new ConfirmPanel(false, false, false, false, false, false, true);
	    pnlButtons.addActionListener(this);

	    Button okBtn = pnlButtons.getButton(ConfirmPanel.A_OK);
	    okBtn.setWidgetListener("onClick", "zAu.cmd0.showBusy(null)");
	    okBtn.addCallback(ComponentCtrl.AFTER_PAGE_DETACHED,
	            t -> ((AbstractComponent) t).setWidgetListener("onClick", null));
	    okBtn.addSclass(ITheme.LOGIN_BUTTON_CLASS);

	    // SHIFT JUST THE BUTTON a bit left (theme margins can be stubborn)
	    Div okWrap = new Div();
	    okWrap.setStyle("display:inline-block; position:relative; left:-8px;"); // tweak -8px as needed
	    okWrap.appendChild(pnlButtons);
	    stack.appendChild(okWrap);

	   
	    stack.appendChild(createTagline());

	    tdField.appendChild(stack);

	    // Disable OK until terms accepted + keep state updated
	    okBtn.setDisabled(true);
	    chkAcceptTerms.addEventListener(Events.ON_CHECK, ev -> updateOkButtonState());
	    if (chkDeclineTerms != null) {
	        chkDeclineTerms.addEventListener(Events.ON_CHECK, ev -> {
	            if (chkDeclineTerms.isChecked()) chkAcceptTerms.setChecked(false);
	            updateOkButtonState();
	        });
	    }
	    updateOkButtonState();

	    // Append the form ONCE
	    this.appendChild(form);
	}
	
	// Builds "Mining Future Skills" tagline with brand colours, all bold, slightly bigger
	private Hbox createTagline() {

	    // Brand palette
	    final String BLUE   = "#2d2c72"; // Mining (dark blue)
	    final String ORANGE = "#e87839"; // Future (rich orange)
	    final String GREEN  = "#3d7a45"; // Skills (organic green)

	    Hbox tagline = new Hbox();
	    tagline.setSpacing("6px");
	    tagline.setStyle("margin-top:12px;"); // just spacing here

	    // Common style: bold + bigger font
	    final String BASE = "font-weight:700; font-size:120%;";

	    Label w1 = new Label("Mining");
	    w1.setStyle(BASE + "color:" + BLUE + ";");

	    Label w2 = new Label("Future");
	    w2.setStyle(BASE + "color:" + ORANGE + ";");

	    Label w3 = new Label("Skills");
	    w3.setStyle(BASE + "color:" + GREEN + ";");

	    tagline.appendChild(w1);
	    tagline.appendChild(w2);
	    tagline.appendChild(w3);

	    return tagline;
	}





	
	private void updateOkButtonState() {
	    Button okBtn = pnlButtons.getButton(ConfirmPanel.A_OK);
	    boolean enable = chkAcceptTerms.isChecked();
	    okBtn.setDisabled(!enable);
	}

	
	private void openTermsAndConditions() {
	    final String path = "/WEB-INF/mqa/TERMS AND CONDITIONS MQA_DG APPLICATION PLATFORM.pdf";
	    try {
	        InputStream is = Executions.getCurrent()
	                .getDesktop().getWebApp().getResourceAsStream(path);
	        if (is == null) {
	            Dialog.warn(0, "URLnotValid", "Terms file not found: " + path, null);
	            return;
	        }
	        AMedia media = new AMedia("TERMS AND CONDITIONS MQA_DG APPLICATION PLATFORM.pdf",
	                                  "pdf", "application/pdf", is);
	        Filedownload.save(media);   // ZK will close the stream afterwards
	        // <-- do NOT close 'is' here
	    } catch (Exception e) {
	        Dialog.warn(0, "URLnotValid", e.getMessage(), null);
	    }
	}
	
	/*
	private void openTermsAndConditions() {
	    // AD_Client record to read the attachment from
	    final int AD_CLIENT_ID = 1000000;  // or Env.getAD_Client_ID(Env.getCtx())
	    final String EXPECTED_FILENAME = "TERMS AND CONDITIONS MQA_DG APPLICATION PLATFORM.pdf";

	    try {
	        // Get attachment on AD_Client
	        MAttachment attachment = MAttachment.get(
	                Env.getCtx(),
	                MClient.Table_ID,   // table = AD_Client
	                AD_CLIENT_ID        // record = 1000000
	        );

	        if (attachment == null || attachment.getEntryCount() == 0) {
	            Dialog.warn(0, "URLnotValid",
	                    "No attachment found on AD_Client_ID=" + AD_CLIENT_ID, null);
	            return;
	        }

	        // Find the desired entry (by name, or fall back to first PDF)
	        int entryIndex = -1;
	        for (int i = 0; i < attachment.getEntryCount(); i++) {
	            String name = attachment.getEntryName(i);
	            if (EXPECTED_FILENAME.equalsIgnoreCase(name)) {
	                entryIndex = i;
	                break;
	            }
	        }

	        // If not found by exact name, optionally take the first .pdf
	        if (entryIndex == -1) {
	            for (int i = 0; i < attachment.getEntryCount(); i++) {
	                String name = attachment.getEntryName(i);
	                if (name != null && name.toLowerCase().endsWith(".pdf")) {
	                    entryIndex = i;
	                    break;
	                }
	            }
	        }

	        if (entryIndex == -1) {
	            Dialog.warn(0, "URLnotValid",
	                    "No PDF attachment found on AD_Client_ID=" + AD_CLIENT_ID, null);
	            return;
	        }

	        String fileName = attachment.getEntryName(entryIndex);
	        byte[] data = attachment.getEntryData(entryIndex);
	        if (data == null || data.length == 0) {
	            Dialog.warn(0, "URLnotValid",
	                    "Attachment data is empty for: " + fileName, null);
	            return;
	        }

	        InputStream is = new ByteArrayInputStream(data);

	        AMedia media = new AMedia(
	                fileName,
	                "pdf",
	                "application/pdf",
	                is
	        );

	        Filedownload.save(media); // ZK will handle the stream
	        // Do not close 'is' here – ZK/AMedia handles it.

	    } catch (Exception e) {
	        Dialog.warn(0, "URLnotValid", e.getMessage(), null);
	    }
	}
	
	*/




	/**
	 * Create components
	 */
    private void initComponents()
    {
        lblUserId = new Label();
        lblUserId.setId("lblUserId");
        lblUserId.setValue("User ID");

        lblPassword = new Label();
        lblPassword.setId("lblPassword");
        lblPassword.setValue("Password");

        lblLanguage = new Label();
        lblLanguage.setId("lblLanguage");
        lblLanguage.setValue("Language");

        txtUserId = new Textbox();
        txtUserId.setId("txtUserId");
        txtUserId.setCols(60);
        txtUserId.setMaxlength(60);
        txtUserId.setClientAttribute("autocomplete", "username");

        txtPassword = new Textbox();
        txtPassword.setId("txtPassword");
        txtPassword.setType("password");
        txtPassword.setCols(60);
        if (MSysConfig.getBooleanValue(MSysConfig.ZK_LOGIN_ALLOW_CHROME_SAVE_PASSWORD, true))
        	txtPassword.setClientAttribute("autocomplete", "current-password");

        lstLanguage = new Combobox();
        lstLanguage.setAutocomplete(true);
        lstLanguage.setAutodrop(true);
        lstLanguage.setId("lstLanguage");
        lstLanguage.addEventListener(Events.ON_SELECT, this);

        // Update Language List
        lstLanguage.getItems().clear();
        ArrayList<String> supported = Env.getLoginLanguages();
        String[] availableLanguages = Language.getNames();
        for (String langName : availableLanguages) {
    		Language language = Language.getLanguage(langName);
   			if (!supported.contains(language.getAD_Language()))
   				continue;
			lstLanguage.appendItem(langName, language.getAD_Language());
		}

        chkRememberMe = new Checkbox(Msg.getMsg(Language.getBaseAD_Language(), "RememberMe"));
        chkRememberMe.setId("chkRememberMe");

        chkSelectRole = new Checkbox(Msg.getMsg(Language.getBaseAD_Language(), "SelectRole"));
        chkSelectRole.setId("chkSelectRole");
        
        btnResetPassword = new A(Msg.getMsg(Language.getBaseAD_Language(), "ForgotMyPassword"));
        btnResetPassword.setId("btnResetPassword");
        
        // Martin 12/08/2025
        btnRegister = new A("Register DG User");
        btnRegister.setId("btnRegister");
        
     // NEW: "Register SDF User" link (opens separate SDFRegistrationWindow)
        btnRegisterSdf = new A("Register SDF User");
        btnRegisterSdf.setId("btnRegisterSdf");
        
     // Same color for all action buttons
     // Same text color for all action buttons
        final String PRIMARY = "#2d2c72";
        final String BTN_STYLE =
            "background: none; " +            // no background
            "border: none; " +                // optional — remove border if desired
            "color: " + PRIMARY + "; " +      // text color only
            "font-weight: 600; " +
            "text-transform: none; " +
            "cursor: pointer;";

        btnResetPassword.setStyle(BTN_STYLE);
        btnRegister.setStyle(BTN_STYLE);
        btnRegisterSdf.setStyle(BTN_STYLE);


        lstLanguage.setVisible(false);

        
        if (lstLanguage.getItems().size() > 0){
        	validLstLanguage = (String)lstLanguage.getItems().get(0).getLabel();
        }           
        
     // --- MQA Terms & Conditions
     // --- MQA Terms & Conditions (checkbox version)
        chkAcceptTerms = new Checkbox();      // label shown in layout with a separate Label
        chkAcceptTerms.setId("chkAcceptTerms");
        chkAcceptTerms.setChecked(false);

        chkDeclineTerms = new Checkbox();
        chkDeclineTerms.setId("chkDeclineTerms");
        chkDeclineTerms.setChecked(false);

        // Underlined hyperlink word "Terms and Conditions"
    
        lnkTerms = new A("Terms and Conditions");
        lnkTerms.setId("lnkTerms");
        lnkTerms.setStyle("text-decoration: underline; cursor: pointer; color: #2d2c72;");
        
        lnkTrainingManual = new A("DG Application Training Manual");

        lnkTrainingManual.setId("lnkTrainingManual");
        lnkTrainingManual.setStyle("text-decoration: underline; cursor: pointer; color: #2d2c72; font-weight:600;");


    }

    @Override
    public void onEvent(Event event)
    {
        Component eventComp = event.getTarget();

        if (event.getTarget().getId().equals(ConfirmPanel.A_OK))
        {
            validateLogin();
        }
        else if (event.getTarget().getId().equals(ConfirmPanel.A_HELP))
        {
            openLoginHelp();
        }
        else if (event.getName().equals(Events.ON_SELECT))
        {
            if(eventComp.getId().equals(lstLanguage.getId())) {            	            	
            	if (lstLanguage.getSelectedItem() == null){
            		lstLanguage.setValue(validLstLanguage);
            	}else{
            		validLstLanguage = lstLanguage.getSelectedItem().getLabel();
            	}
            	           	
            	languageChanged(validLstLanguage);
            }
        }
        else if (event.getTarget() == btnResetPassword)
        {
        	btnResetPasswordClicked();
        }
        // Martin 18/8/2025
        else if (event.getTarget() == btnRegister) {
        	GeneralRegistrationWindow.show(this,1000023);  // agent role
        }
     // NEW: open SDF-specific registration window (no changes to original RegistrationWindow)
        else if (event.getTarget() == btnRegisterSdf) {
        	GeneralRegistrationWindow.show(this,1000042);  // SDF Role
        }

        else if (event.getName().equals(ON_LOAD_TOKEN)) 
        {
        	BrowserToken.load(txtUserId);
        	
        	txtUserId.setEnabled(true);
            txtPassword.setEnabled(true);
            lstLanguage.setEnabled(true);
            
        	AuFocus auf = new AuFocus(txtUserId);
            Clients.response(auf);
        }
        //
    }

	private void openLoginHelp() {
		String lang = (String) lstLanguage.getSelectedItem().getValue();
		lang = lang.substring(0, 2);
		String helpURL = MSysConfig.getValue(MSysConfig.LOGIN_HELP_URL, "https://wiki.idempiere.org/{lang}/Login_Help");
		if (helpURL.contains("{lang}")) {
			String rawURL = helpURL;
			helpURL = Util.replace(rawURL, "{lang}", lang);
			if (!"en".equals(lang) && !WebUtil.isUrlOk(helpURL))
				helpURL = Util.replace(rawURL, "{lang}", "en"); // default to English
		}
		try {
			Executions.getCurrent().sendRedirect(helpURL, "_blank");
		}
		catch (Exception e) {
			String message = e.getMessage();
			Dialog.warn(0, "URLnotValid", message, null);
		}
	}

	/**
	 * User id from onUserToken event.
	 * @param AD_User_ID
	 */
	private void onUserIdChange(int AD_User_ID) {
		String userName = txtUserId.getValue();
		if (userName != null && userName.length() > 0 && AD_User_ID < 0)
		{
			String column;
			if (email_login)
				column = "COALESCE(LDAPUser,EMail)";
			else
				column = "COALESCE(LDAPUser,Name)";
			List<MUser> users = new Query(Env.getCtx(), MUser.Table_Name, "Password IS NOT NULL AND IsActive='Y' AND " + column + "=?", null)
				.setParameters(userName)
				.list();
			if (users.size() == 1) {
				AD_User_ID = users.get(0).getAD_User_ID();
			}
		}
		if (AD_User_ID >= 0)
		{
			// Elaine 2009/02/06 Load preference from AD_Preference
			UserPreference userPreference = SessionManager.getSessionApplication().loadUserPreference(AD_User_ID);
			String initDefault = userPreference.getProperty(UserPreference.P_LANGUAGE);
			for(int i = 0; i < lstLanguage.getItemCount(); i++)
			{
				Comboitem li = lstLanguage.getItemAtIndex(i);
				if (li.getLabel().equals(initDefault) || li.getValue().equals(initDefault))
				{
					lstLanguage.setSelectedIndex(i);
					languageChanged(li.getLabel());
					break;
				}
			}
		}
	}

	/**
	 * Apply language change to UI elements
	 * @param langName
	 */
    private void languageChanged(String langName)
    {
    	Language language = findLanguage(langName);
		
		if (email_login)
			lblUserId.setValue(Msg.getMsg(language, "EMail"));
		else
			lblUserId.setValue(Msg.getMsg(language, "User"));
    	lblPassword.setValue(Msg.getMsg(language, "Password"));
    	lblLanguage.setValue(Msg.getMsg(language, "Language"));
    	chkRememberMe.setLabel(Msg.getMsg(language, "RememberMe"));
    	chkSelectRole.setLabel(Msg.getMsg(language, "SelectRole"));
    	btnResetPassword.setLabel(Msg.getMsg(language, "ForgotMyPassword"));
    	lblLogin.setValue(Msg.getMsg(language, "LoginHeader"));
    	pnlButtons.getButton(ConfirmPanel.A_OK).setLabel(Util.cleanAmp(Msg.getMsg(language, ConfirmPanel.A_OK)));
    	//pnlButtons.getButton(ConfirmPanel.A_HELP).setLabel(Util.cleanAmp(Msg.getMsg(language, ConfirmPanel.A_HELP)));
    }

    /**
     * Find language by name
     * @param langName
     * @return Language
     */
	private Language findLanguage(String langName) {
		Language tmp = Language.getLanguage(langName);
    	Language language = new Language(tmp.getName(), tmp.getAD_Language(), tmp.getLocale(), tmp.isDecimalPoint(),
    			tmp.getDateFormat().toPattern(), tmp.getMediaSize());
    	Env.verifyLanguage(ctx, language);
    	Env.setContext(ctx, Env.LANGUAGE, language.getAD_Language());
    	Env.setContext(ctx, AEnv.LOCALE, language.getLocale().toString());

    	//cph::erp added this in order to get the processing dialog in the correct language
    	Locale locale = language.getLocale();
    	try {
				Clients.reloadMessages(locale);
		} catch (IOException e) {
			logger.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
        Locales.setThreadLocal(locale);
    	
		return language;
	}
	
    /**
     * Validates user name and password when logging in
     */
    public void validateLogin()
    {
        Login login = new Login(ctx);
     // Enforce Terms acceptance per spec
     // Enforce Terms acceptance per spec
        if (!chkAcceptTerms.isChecked()) {
            Clients.clearBusy();
            throw new WrongValueException("Please accept the MQA Terms and Conditions to continue.");
        }


        String userId = txtUserId.getValue();
        String userPassword = txtPassword.getValue();

        //check is token
        String token = (String) txtPassword.getAttribute("user.token.hash");
        if (token != null && token.equals(userPassword))
        {
        	userPassword = "";
        	int AD_Session_ID = (Integer)txtPassword.getAttribute("user.token.sid");
        	MSession session = new MSession(Env.getCtx(), AD_Session_ID, null);
        	if (session.get_ID() == AD_Session_ID)
        	{
        		MUser user = MUser.get(Env.getCtx(), session.getCreatedBy());
        		if (BrowserToken.validateToken(session, user, token))
        		{
        			userPassword = user.getPassword();
        		}
        	}
        }

        Session currSess = Executions.getCurrent().getDesktop().getSession();
        
        KeyNamePair clientsKNPairs[] = login.getClients(userId, userPassword, ROLE_TYPES_WEBUI);
        
        if (clientsKNPairs == null || clientsKNPairs.length == 0)
        {
        	String loginErrMsg = login.getLoginErrMsg();
        	if (Util.isEmpty(loginErrMsg))
        		loginErrMsg = Msg.getMsg(ctx,"FailedLogin", true);

        	// IDEMPIERE-617
            String x_Forward_IP = Executions.getCurrent().getHeader("X-Forwarded-For");
            if (x_Forward_IP == null) {
            	 x_Forward_IP = Executions.getCurrent().getRemoteAddr();
            }
        	logAuthFailure.log(x_Forward_IP, "/webui", userId, loginErrMsg);

			// Incremental delay to avoid brute-force attack on testing codes
			try {
				Thread.sleep(failures * 2000);
			} catch (InterruptedException e) {}
			failures++;
        	Clients.clearBusy();
       		throw new WrongValueException(loginErrMsg);
        }
        else
        {
            if (clientsKNPairs.length == 1) {
            	Env.setContext(Env.getCtx(), Env.AD_CLIENT_ID, (String) clientsKNPairs[0].getID());
            	MUser user = MUser.get(Env.getCtx(), Login.getAppUser(userId));
            	if (user != null)
            		Env.setContext(Env.getCtx(), Env.AD_USER_ID, user.getAD_User_ID() );
            }

        	String langName = null;
        	if ( lstLanguage.getSelectedItem() != null )
        		langName = (String) lstLanguage.getSelectedItem().getLabel();
        	else
        		langName = Language.getBaseLanguage().getName();
        	Language language = findLanguage(langName);
            Env.setContext(ctx, UserPreference.LANGUAGE_NAME, language.getName()); // Elaine 2009/02/06

            if (login.isPasswordExpired())
            	wndLogin.changePassword(userId, userPassword, chkSelectRole.isChecked(), clientsKNPairs);
            else
            	wndLogin.loginOk(userId, chkSelectRole.isChecked(), clientsKNPairs);

            Locale locale = language.getLocale();
            currSess.setAttribute(Attributes.PREFERRED_LOCALE, locale);
            try {
				Clients.reloadMessages(locale);
			} catch (IOException e) {
				logger.log(Level.WARNING, e.getLocalizedMessage(), e);
			}
            Locales.setThreadLocal(locale);

            String timeoutText = getUpdateTimeoutTextScript();
            if (!Strings.isEmpty(timeoutText))
            	Clients.response("browserTimeoutScript", new AuScript(null, timeoutText));
        }

		// This validation code is added to check the reported bug
		// [ adempiere-ZK Web Client-2832968 ] User context lost?
		// https://sourceforge.net/p/adempiere/zk-web-client/303/
		// it's harmless, if there is no bug then this must never fail        
        currSess.setAttribute(AdempiereWebUI.CHECK_AD_USER_ID_ATTR, Env.getAD_User_ID(ctx));

        /* Check DB version */
        String version = DB.getSQLValueString(null, "SELECT Version FROM AD_System");
        //  Identical DB version
        if (! Adempiere.DB_VERSION.equals(version)) {
            String AD_Message = "DatabaseVersionError";
            //  Code assumes Database version {0}, but Database has Version {1}.
            String msg = Msg.getMsg(ctx, AD_Message, new Object[] {Adempiere.DB_VERSION, version});   //  complete message
            throw new ApplicationException(msg);
        }

    }

    /**
     * @return client side timeout script
     */
	private String getUpdateTimeoutTextScript() {
		String msg = Msg.getMsg(Env.getCtx(), "SessionTimeoutText").trim();  //IDEMPIERE-847
		String continueNsg = Msg.getMsg(Env.getCtx(), "continue").trim();   //IDEMPIERE-847
		if (msg == null || msg.equals("SessionTimeoutText")) {
			return null;
		}
		msg = Strings.escape(msg, "\"");
		String s = "adempiere.set(\"zkTimeoutText\", \"" + msg + "\");";//IDEMPIERE-847
		s = s + " adempiere.set(\"zkContinueText\", \"" + continueNsg + "\");"; //IDEMPIERE-847
		return s;
	}
	
	/**
	 * Handle reset password event
	 */
	private void btnResetPasswordClicked()
	{
		String userId = Login.getAppUser(txtUserId.getValue());
		if (Util.isEmpty(userId))
    		throw new IllegalArgumentException(Msg.getMsg(ctx, "FillMandatory") + " " + lblUserId.getValue());
		
		boolean email_login = MSysConfig.getBooleanValue(MSysConfig.USE_EMAIL_FOR_LOGIN, false);
    	StringBuilder whereClause = new StringBuilder("Password IS NOT NULL AND ");
		if (email_login)
			whereClause.append("COALESCE(LDAPUser,EMail)=?");
		else
			whereClause.append("COALESCE(LDAPUser,Name)=?");
		whereClause.append(" AND")
				.append(" EXISTS (SELECT * FROM AD_User_Roles ur")
				.append("         INNER JOIN AD_Role r ON (ur.AD_Role_ID=r.AD_Role_ID)")
				.append("         WHERE ur.AD_User_ID=AD_User.AD_User_ID AND ur.IsActive='Y' AND r.IsActive='Y') AND ")
				.append(" EXISTS (SELECT * FROM AD_Client c")
				.append("         WHERE c.AD_Client_ID=AD_User.AD_Client_ID")
				.append("         AND c.IsActive='Y') AND ")
				.append(" AD_User.IsActive='Y'")
				.append(" AND AD_User.SecurityQuestion IS NOT NULL")
				.append(" AND AD_User.Answer IS NOT NULL");

		List<MUser> users;
		try {
			PO.setCrossTenantSafe();
			users = new Query(ctx, MUser.Table_Name, whereClause.toString(), null)
					.setParameters(userId)
					.setOrderBy(MUser.COLUMNNAME_AD_User_ID)
					.list();
		} finally {
			PO.clearCrossTenantSafe();
		}

		wndLogin.resetPassword(userId, users.size() == 0);
	}

	/**
	 * Get default languages from the browser
	 * @param header 
	 */
	private List<String> browserLanguages(String header) {
		List<String> arrstr = new ArrayList<String>();
		if (header == null)
			return arrstr;

		for (String str : header.split(",")){
			String[] arr = str.trim().replace("-", "_").split(";");

			for (String s : arr){
				s = s.trim();
				if (!s.startsWith("q=")) {
					if (s.contains("_") && s.length() == 5) {
						String baselang = s.substring(0, 2).toLowerCase();
						StringBuffer lang = new StringBuffer(baselang).append("_").append(s.substring(3).toUpperCase());
						if (!arrstr.contains(lang.toString()))
							arrstr.add(lang.toString());
						if (!arrstr.contains(baselang))
							arrstr.add(baselang);
					} else {
						if (s.length() == 2 && !arrstr.contains(s.toLowerCase()))
							arrstr.add(s.toLowerCase());
					}
				}
			}
		}
		return arrstr;
	}
	
	private static int getCurrentFundingPolicyId() {
	    final String sql =
	            "SELECT ZZ_Open_Application_ID " +
	            "FROM adempiere.ZZ_Open_Application " +
	            "WHERE IsActive='Y' " +
	            "  AND now() BETWEEN StartDate AND EndDate " +
	            "  AND ZZ_DocStatus='AP' " +
	            "ORDER BY StartDate DESC, EndDate DESC, ZZ_Open_Application_ID DESC " +
	            "LIMIT 1";
	    return DB.getSQLValue(null, sql);
	}

	private static MAttachment getCurrentOpenAttachment() {
	    int x_ZZ_Open_Application_ID = getCurrentFundingPolicyId();
	    if (x_ZZ_Open_Application_ID <= 0) return null;

	    X_ZZ_Open_Application x_ZZ_Open_Application = new X_ZZ_Open_Application(Env.getCtx(), x_ZZ_Open_Application_ID, null);
	    return MAttachment.get(Env.getCtx(), x_ZZ_Open_Application.get_Table_ID(), x_ZZ_Open_Application.get_ID());
	}

	private static MAttachmentEntry pickTrainingEntry(MAttachment attachment) {
	    if (attachment == null || attachment.getEntryCount() == 0) return null;

	    for (int i = attachment.getEntryCount() - 1; i >= 0; i--) {
	        MAttachmentEntry e = attachment.getEntry(i);
	        if (e == null || e.getData() == null) continue;

	        String name = e.getName();
	        String safeName = name == null ? "" : name.trim();

	        	        
	        boolean isTraining = safeName.toLowerCase().contains("manual");
	        if (isTraining) return e;

	    }
	    return null;
	}

	private static void downloadEntry(MAttachmentEntry entry, String defaultFileName) {
	    if (entry == null || entry.getData() == null) {
	        Clients.showNotification("Document not found.", "warning", null, "top_center", 3500);
	        return;
	    }

	    byte[] data = entry.getData();
	    
	    String fileName = sanitizeFileName(entry.getName(), defaultFileName);

	    // Extract extension (after sanitizing!)
	    String ext = "pdf";
	    int dot = fileName.lastIndexOf('.');
	    if (dot >= 0 && dot < fileName.length() - 1) {
	        ext = fileName.substring(dot + 1).toLowerCase();
	    }	  

	    String contentType;
	    switch (ext) {
	    case "pdf":  contentType = "application/pdf"; break;
	    case "doc":  contentType = "application/msword"; break;
	    case "docx": contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; break;
	    default:     contentType = "application/octet-stream"; break;
	    }

	    Filedownload.save(new AMedia(fileName, ext, contentType, data));
	}

	private static void downloadTrainingManual() {
	    MAttachment attachment = getCurrentOpenAttachment();
	    if (attachment == null) {
	        Clients.showNotification(
	                "No Training Manual found..",
	                "warning", null, "top_center", 3500);
	        return;
	    }

	    MAttachmentEntry entry = pickTrainingEntry(attachment);
	    if (entry == null) {
	        Clients.showNotification(
	                "No Training Manual found.",
	                "warning", null, "top_center", 3500);
	        return;
	    }

	    downloadEntry(entry, "TrainingManual.pdf");
	}

	
	private static boolean hasTrainingManual() {
	    MAttachment att = getCurrentOpenAttachment();
	    return pickTrainingEntry(att) != null;
	}
	
	private static String sanitizeFileName(String name, String fallback) {
	    if (name == null) return fallback;

	    // Normalize common invisible junk
	    String s = name
	            .replace('\u00A0', ' ')      // NBSP -> space
	            .replace("\u200B", "")       // zero-width space
	            .replace("\uFEFF", "")       // BOM
	            .trim();

	    if (s.isEmpty()) return fallback;

	    // Strip markdown-ish wrappers and other common wrappers at BOTH ends
	    // (underscores, tildes, backticks, quotes)
	    while (!s.isEmpty()) {
	        char first = s.charAt(0);
	        char last  = s.charAt(s.length() - 1);

	        boolean stripFirst = first == '_' || first == '~' || first == '`' || first == '"' || first == '\'' || Character.isWhitespace(first);
	        boolean stripLast  = last  == '_' || last  == '~' || last  == '`' || last  == '"' || last  == '\'' || Character.isWhitespace(last);

	        if (!stripFirst && !stripLast) break;

	        if (stripFirst) s = s.substring(1);
	        if (!s.isEmpty() && stripLast) s = s.substring(0, s.length() - 1);
	        s = s.trim();
	    }

	    return s.isEmpty() ? fallback : s;
	}


	
	

}
