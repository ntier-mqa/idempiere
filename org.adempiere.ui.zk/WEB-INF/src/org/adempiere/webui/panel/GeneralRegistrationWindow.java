package org.adempiere.webui.panel;

import java.util.HashMap;
import java.util.Map;

import org.adempiere.webui.window.FDialog;
import org.compiere.model.MClient;
import org.compiere.model.MMailText;
import org.compiere.model.MUser;
import org.compiere.model.MUserRoles;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.EMail;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zul.Button;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;
import org.zkoss.zul.Window;

/**
 * GeneralRegistrationWindow
 * -------------------------
 * Self-contained registration window that assigns a GIVEN role (AD_Role_ID).
 *
 * Logic:
 *  - If email exists: assign the role to existing user (no OTP).
 *  - If email is new: OTP -> create user -> assign the role.
 *
 * Call as:
 *   GeneralRegistrationWindow.show(this, <AD_Role_ID>);
 */
public class GeneralRegistrationWindow extends Window implements org.zkoss.zk.ui.event.EventListener<Event> {

    private static final long serialVersionUID = 1L;
    private static final CLogger log = CLogger.getCLogger(GeneralRegistrationWindow.class);

    // Mail templates (R_MailText.Name)
    private static final String OTP_MAIL_TEXT_NAME     = "REGISTRATION_OTP";
    private static final String WELCOME_MAIL_TEXT_NAME = "REGISTRATION_WELCOME";

    // Configure for your instance
    private static final int DEFAULT_CLIENT_ID = 1000000; // <-- your AD_Client_ID

    // Role context
    private final int roleId;
    private final String roleName; // Resolved from AD_Role for UI/messages

    // UI
    private Textbox txtName;
    private Textbox txtIDNo;
    private Textbox txtPassportNo;
    private Textbox txtCellNo;
    private Textbox txtEmail;
    private Textbox txtOtp;
    private Button  btnSendOtp;
    private Button  btnRegisterUser;

    // ---------- Construction & Launch ----------
    private GeneralRegistrationWindow(int roleId) {
        this.roleId = roleId;
        this.roleName = resolveRoleName(roleId);
        setTitle("Register User for " + roleName);
        setWidth("700px");
        setClosable(true);
        setSizable(false);
        setBorder("normal");
        setId("generalRegistrationWindow_" + roleId);
        buildUI();
        wireEvents();
    }

    public static void show(Component attachTo, int roleId) {
        GeneralRegistrationWindow w = new GeneralRegistrationWindow(roleId);
        if (attachTo != null && attachTo.getPage() != null) {
            attachTo.appendChild(w);
            w.setMode(Window.MODAL);
        } else {
            Page p = Executions.getCurrent().getDesktop().getPages().iterator().next();
            w.setPage(p);
            w.setMode(Window.MODAL);
        }
    }

    private String resolveRoleName(int roleId) {
        String n = DB.getSQLValueString(null,
            "SELECT Name FROM AD_Role WHERE AD_Role_ID=?",
            roleId);
        return n != null && !n.trim().isEmpty() ? n.trim() : ("Role ID " + roleId);
    }

    // ---------- UI ----------
    private void buildUI() {
        Vbox form = new Vbox();
        form.setSpacing("8px");
        form.setWidth("100%");

        txtName = new Textbox();
        txtName.setPlaceholder(Msg.getMsg(Env.getCtx(), "FullName"));
        txtName.setWidth("600px");
        form.appendChild(txtName);

        txtIDNo = new Textbox();
        txtIDNo.setPlaceholder(Msg.getMsg(Env.getCtx(), "IDNumber"));
        txtIDNo.setMaxlength(13);
        txtIDNo.setWidth("300px");
        form.appendChild(txtIDNo);

        txtPassportNo = new Textbox();
        txtPassportNo.setPlaceholder(Msg.getMsg(Env.getCtx(), "PassportNumber"));
        txtPassportNo.setWidth("300px");
        form.appendChild(txtPassportNo);

        txtCellNo = new Textbox();
        txtCellNo.setPlaceholder(Msg.getMsg(Env.getCtx(), "CellNumber"));
        txtCellNo.setMaxlength(10);
        txtCellNo.setWidth("300px");
        form.appendChild(txtCellNo);

        txtEmail = new Textbox();
        txtEmail.setPlaceholder(Msg.getMsg(Env.getCtx(), "Email"));
        txtEmail.setWidth("600px");
        form.appendChild(txtEmail);

        btnSendOtp = new Button(Msg.getMsg(Env.getCtx(), "SendOtp"));
        btnSendOtp.setDisabled(true);
        form.appendChild(btnSendOtp);

        txtOtp = new Textbox();
        txtOtp.setPlaceholder(Msg.getMsg(Env.getCtx(), "EnterOTP"));
        txtOtp.setWidth("220px");
        form.appendChild(txtOtp);

        btnRegisterUser = new Button(Msg.getMsg(Env.getCtx(), "RegisterMe"));
        btnRegisterUser.setDisabled(true);
        form.appendChild(btnRegisterUser);

        this.appendChild(form);
    }

    private void wireEvents() {
    	
    	// ---- Live mutual exclusion while typing (instant toggle) ----
    	txtIDNo.addEventListener(Events.ON_CHANGING, ev -> {
    	    InputEvent iev = (InputEvent) ev;
    	    String v = nvl(iev.getValue());
    	    boolean hasText = !v.isEmpty();
    	    txtPassportNo.setDisabled(hasText);
    	    // Optional: clearWrongValue(txtPassportNo);
    	    updateButtonsState();
    	});

    	txtPassportNo.addEventListener(Events.ON_CHANGING, ev -> {
    	    InputEvent iev = (InputEvent) ev;
    	    String v = nvl(iev.getValue());
    	    boolean hasText = !v.isEmpty();
    	    txtIDNo.setDisabled(hasText);
    	    // Optional: clearWrongValue(txtIDNo);
    	    updateButtonsState();
    	});

        // Mutually exclusive ID/Passport UX
            
        txtIDNo.addEventListener(Events.ON_CHANGE, ev -> {
        	try {
                String v = nvl(txtIDNo.getValue());
                if (!v.isEmpty()) {
                    validateIdNo();                 // throws WrongValueException if invalid
                    txtPassportNo.setDisabled(true);
                } else {
                    txtPassportNo.setDisabled(false);
                }
            } finally {
                updateButtonsState();
            }
        });

	    txtPassportNo.addEventListener(Events.ON_CHANGE, ev -> {
	    	try {
	            if (!txtPassportNo.getValue().trim().isEmpty()) {
	                txtIDNo.setDisabled(true);
	            } else {
	                txtIDNo.setDisabled(false);
	            }
	        } finally {
	            updateButtonsState();
	        }
	    });
	    
	    txtName.addEventListener(Events.ON_CHANGE, ev -> updateButtonsState());
	    
	 
	 // Mobile number – validate on blur
	    txtCellNo.addEventListener(Events.ON_CHANGE, ev -> {
	        try {
	            validateCellNo();                   // throws if invalid
	        } finally {
	            updateButtonsState();
	        }
	    });

	    txtEmail.addEventListener(Events.ON_CHANGE, ev -> {
	        try {
	            validateEmailOnBlur();              // throws if invalid or duplicate
	        } finally {
	            updateButtonsState();
	        }
	    });
	 // OTP – just presence/format (6 digits) for enabling Register
	    txtOtp.addEventListener(Events.ON_CHANGE, ev -> updateButtonsState());
        btnSendOtp.addEventListener(Events.ON_CLICK, this);
        btnRegisterUser.addEventListener(Events.ON_CLICK, this);
    }

    @Override
    public void onEvent(Event event) throws Exception {
        if (event.getTarget() == btnSendOtp) {
            onSendOtp();
        } else if (event.getTarget() == btnRegisterUser) {
            onRegister();
        }
    }
    
    

    // ---------- OTP SEND ----------
    private void onSendOtp() {
        String email = nvl(txtEmail.getValue());
        if (email.isEmpty())
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "FillEmailFirst"));

        if (!isCoreFieldsValid()) {
        	 String msg = Msg.getMsg(Env.getCtx(), "CompleteFieldsBeforeOTP");
             if (msg == null || "CompleteFieldsBeforeOTP".equals(msg)) {
                 msg = "Please complete all required fields (Name, ID/Passport, Mobile, valid Email) before requesting an OTP.";
             }
             throw new IllegalArgumentException(msg);
        }

        if (isEmailRegistered(email))
            throw new IllegalArgumentException("This email already exists. Use Register to assign the " + roleName + " role.");

        String otp = String.valueOf((int)(Math.random() * 900000) + 100000);
        Executions.getCurrent().getSession().setAttribute("OTP_CODE", otp);

        Map<String,String> vars = new HashMap<>();
        vars.put("OTP", otp);
        vars.put("EMail", email);
        vars.put("FullName", nvl(txtName.getValue()));

        boolean ok = sendWithTemplate(email, OTP_MAIL_TEXT_NAME, vars, null);
        if (!ok)
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "OtpSendFailed"));

        FDialog.info(0, this, Msg.getMsg(Env.getCtx(), "OtpSent", new Object[]{ email }));
    }

    // ---------- REGISTER ----------
    private void onRegister() {
        String name       = nvl(txtName.getValue());
        String idNo       = nvl(txtIDNo.getValue());
        String passportNo = nvl(txtPassportNo.getValue());
        String cellNo     = nvl(txtCellNo.getValue());
        String email      = nvl(txtEmail.getValue());
        String otp        = nvl(txtOtp.getValue());

        if (email.isEmpty())
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "FillEmailFirst"));

        boolean exists = isEmailRegistered(email);
        // Martin 2026-10-05: Restored dual-role support. Previously this unconditionally
        // blocked registration with "email already registered" whenever the AD_User row
        // existed, regardless of role - so a DG user could never also register as SDF
        // (and vice versa). Re-enabled the existing-user path below (was commented out)
        // so an existing user who doesn't yet hold the target role gets it assigned.
        if (exists) {
            // Existing user path: check if role already present
            int adUserId = getUserIdByEmail(email);
            if (adUserId <= 0) {
                throw new IllegalArgumentException("Unable to find existing user.");
            }

            if (hasRole(adUserId, roleId)) {
                FDialog.info(0, this, "Role '" + roleName + "' already exists for this user.");
                detach();
                return;
            }

            // Does not have role yet → assign role (and update optional fields)
            assignRoleToExistingUser(email, roleId, cellNo, idNo, passportNo);
            FDialog.info(0, this, roleName + " role assigned.");
            detach();
            return;
        }

        // New user -> OTP path
        if (name.isEmpty() || cellNo.isEmpty() || otp.isEmpty())
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "FillRequiredFields"));
        validateCellNo();
        if (idNo.isEmpty() && passportNo.isEmpty())
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "EnterIdOrPassport"));
        if (!idNo.isEmpty() && !idNo.matches("\\d{13}"))
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "InvalidIdNumber"));

        String storedOtp = (String) Executions.getCurrent().getSession().getAttribute("OTP_CODE");
        if (storedOtp == null || !storedOtp.equals(otp))
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "InvalidOtp"));

        // Create user
        MUser user = new MUser(Env.getCtx(), 0, null);
        user.setName(name);
        user.setPhone(cellNo);
        user.setEMail(email);
        user.setIsActive(true);
        user.set_ValueNoCheck(MUser.COLUMNNAME_AD_Client_ID, DEFAULT_CLIENT_ID);
        user.set_ValueOfColumn("ZZ_ID_Passport_No", idNo);
        user.set_ValueOfColumn("ZZ_Passport_No",   passportNo);
        String tempPwd = PasswordGenerator.generatePassword(8);
        user.setPassword(tempPwd);
        user.setNotificationType(user.NOTIFICATIONTYPE_EMailPlusNotice);
        user.setIsExpired(true);
        user.saveEx();

        // Assign role
        MUserRoles ur = new MUserRoles(Env.getCtx(), 0, null);
        ur.setAD_User_ID(user.getAD_User_ID());
        ur.setAD_Role_ID(roleId);
        ur.setIsActive(true);
        ur.set_ValueNoCheck(MUserRoles.COLUMNNAME_AD_Client_ID, DEFAULT_CLIENT_ID);
        ur.saveEx();

        // Welcome mail
        Map<String,String> vars = new HashMap<>();
        vars.put("FullName", user.getName());
        vars.put("TempPassword", user.getPassword());
        vars.put("EMail", user.getEMail());
        sendWithTemplate(user.getEMail(), WELCOME_MAIL_TEXT_NAME, vars, user);

        FDialog.info(0, this, Msg.getMsg(Env.getCtx(), "RegistrationSuccess"));
        detach();
    }

    // ---------- Helpers ----------
    private void assignRoleToExistingUser(String email, int roleId, String cellNo, String idNo, String passportNo) {
        int adUserId = DB.getSQLValue(null,
            "SELECT AD_User_ID FROM AD_User WHERE IsActive='Y' AND AD_Client_ID=? AND UPPER(TRIM(EMail))=UPPER(TRIM(?))",
            DEFAULT_CLIENT_ID, email);
        if (adUserId <= 0)
            throw new IllegalArgumentException("Unable to find existing user.");

        int has = DB.getSQLValue(null,
            "SELECT COUNT(*) FROM AD_User_Roles WHERE AD_User_ID=? AND AD_Role_ID=? AND IsActive='Y'",
            adUserId, roleId);
        if (has == 0) {
            MUserRoles ur = new MUserRoles(Env.getCtx(), 0, null);
            ur.setAD_User_ID(adUserId);
            ur.setAD_Role_ID(roleId);
            ur.setIsActive(true);
            ur.set_ValueNoCheck(MUserRoles.COLUMNNAME_AD_Client_ID, DEFAULT_CLIENT_ID);
            ur.saveEx();
        }
        if (!cellNo.isEmpty())
            DB.executeUpdateEx("UPDATE AD_User SET Phone=? WHERE AD_User_ID=?", new Object[]{cellNo, adUserId}, null);
        if (idNo.matches("\\d{13}"))
            DB.executeUpdateEx("UPDATE AD_User SET ZZ_ID_Passport_No=? WHERE AD_User_ID=?", new Object[]{idNo, adUserId}, null);
        if (!passportNo.isEmpty())
            DB.executeUpdateEx("UPDATE AD_User SET ZZ_Passport_No=? WHERE AD_User_ID=?", new Object[]{passportNo, adUserId}, null);
    }

    private boolean sendWithTemplate(String toEMail, String templateName, Map<String,String> ctxVars, MUser userOrNull) {
        int clientId = userOrNull != null ? userOrNull.getAD_Client_ID() : Env.getAD_Client_ID(Env.getCtx());
        int mailTextId = DB.getSQLValue(null,
            "SELECT R_MailText_ID FROM R_MailText WHERE IsActive='Y' AND Name=? AND AD_Client_ID IN (?,0) ORDER BY AD_Client_ID",
            templateName, clientId);
        if (mailTextId <= 0) return false;

        MMailText mailText = new MMailText(Env.getCtx(), mailTextId, null);
        mailText.setLanguage(Env.getContext(Env.getCtx(), Env.LANGUAGE));
        if (userOrNull != null) mailText.setUser(userOrNull);
        if (ctxVars != null) for (Map.Entry<String,String> e : ctxVars.entrySet())
            Env.setContext(Env.getCtx(), "#" + e.getKey(), e.getValue() == null ? "" : e.getValue().trim());

        String body = mailText.getMailText(true, true, true);
        body = Env.parseVariable(body, userOrNull, null, true);

        if (ctxVars != null) for (String k : ctxVars.keySet()) Env.setContext(Env.getCtx(), "#" + k, "");

        MClient client = MClient.get(Env.getCtx());
        EMail email = client.createEMail(toEMail, mailText.getMailHeader(), body, mailText.isHtml());
        if (mailText.isHtml()) email.setMessageHTML(mailText.getMailHeader(), body);
        else { email.setSubject(mailText.getMailHeader()); email.setMessageText(body); }
        if (!email.isValid() && !email.isValid(true)) return false;
        return EMail.SENT_OK.equals(email.send());
    }

    private boolean isNameValid() { return !nvl(txtName.getValue()).isEmpty(); }
    private boolean isCellValid() { return nvl(txtCellNo.getValue()).matches("\\d{10}"); }
    private boolean isEmailValid() {
        String email = nvl(txtEmail.getValue());
        // Martin 2026-10-05: No longer treating an already-registered email as invalid here.
        // Dual-role registration (e.g. an existing DG user registering as SDF) needs the
        // Register button enabled for a known email too - only format is checked now;
        // the existing-user-vs-new-user branching happens in onRegister()/updateButtonsState().
        return email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private boolean isIdOrPassportValid() {
    	String id = nvl(txtIDNo.getValue());
        String pass = nvl(txtPassportNo.getValue());

        if (id.isEmpty() && pass.isEmpty()) return false;   // need one
        if (!id.isEmpty() && !pass.isEmpty()) return false; // only one allowed

        if (!id.isEmpty()) {
            if (!id.matches("\\d{13}")) return false;
            ZZ_SA_IDNumber sa = new ZZ_SA_IDNumber(id);
            return sa.getIDNumber() != null && sa.isIDNumberValid();
        }
        // Passport provided (no extra format rules here)
        return true;
    }
    private boolean isOtpEntered() { return nvl(txtOtp.getValue()).matches("\\d{6}"); }
    private boolean isCoreFieldsValid() {
        return isNameValid() && isIdOrPassportValid() && isCellValid() && isEmailValid();
    }

    private void updateButtonsState() {
        boolean coreValid = isCoreFieldsValid();
        String email = nvl(txtEmail.getValue());
        boolean emailLooksOk = isEmailValid();
        boolean emailExists = emailLooksOk && isEmailRegistered(email);

        // Lock/unlock OTP field based on whether the email already exists
        if (emailExists) {
            txtOtp.setReadonly(true);
            txtOtp.setValue(""); // clear any stray code
        } else {
            txtOtp.setReadonly(false);
        }
        btnSendOtp.setDisabled(!coreValid || emailExists);
        boolean allowRegister = coreValid && (emailExists || isOtpEntered());
        btnRegisterUser.setDisabled(!allowRegister);
    }

    private void validateCellNo() {
    	String cell = nvl(txtCellNo.getValue());
        // exactly 10 digits, no spaces, no symbols
        if (!cell.matches("\\d{10}")) {
            // AD_Message key recommended: "CellMustBe10Digits"
            String msg = Msg.getMsg(Env.getCtx(), "CellMustBe10Digits", new Object[0]);
            if (msg == null || msg.equals("CellMustBe10Digits")) {
                msg = "Mobile number must be exactly 10 digits (digits only).";
            }
            throw new WrongValueException(txtCellNo, msg);
        }        
    }

 
    
    private void validateEmailOnBlur() {
        String email = nvl(txtEmail.getValue());
        if (email.isEmpty())
            return;

        // 1) Basic format check
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            String msg = Msg.getMsg(Env.getCtx(), "InvalidEMail");
            if (msg == null || "InvalidEMail".equals(msg)) {
                msg = "Please enter a valid email address.";
            }
            throw new WrongValueException(txtEmail, msg);
        }

        // Martin 2026-10-05: Removed the "already registered" rejection that used to run here.
        // It blocked dual-role registration (e.g. a DG user trying to also register as SDF)
        // before the form could even be submitted. onRegister() now handles an already-
        // registered email by assigning the additional role instead of rejecting it outright.
    }

    
    private void validateIdNo() {
        String id = nvl(txtIDNo.getValue());

        // Fast guard for length/digits so we can give an immediate, clear message
        if (!id.matches("\\d{13}")) {
            // AD_Message key suggested: "InvalidIdNumber"
            String msg = Msg.getMsg(Env.getCtx(), "InvalidIdNumber");
            if (msg == null || "InvalidIdNumber".equals(msg)) {
                msg = "ID number must be exactly 13 digits.";
            }
            throw new WrongValueException(txtIDNo, msg);
        }

        ZZ_SA_IDNumber sa = new ZZ_SA_IDNumber(id);
        // Constructor leaves ID null if basic format fails; also do full CDV/DOB validation
        boolean ok = sa.getIDNumber() != null && sa.isIDNumberValid();
        if (!ok) {
            String msg = Msg.getMsg(Env.getCtx(), "InvalidIdNumber");
            if (msg == null || "InvalidIdNumber".equals(msg)) {
                msg = "Invalid South African ID number (date/check digit failed).";
            }
            throw new WrongValueException(txtIDNo, msg);
        }
    }

    private boolean isEmailRegistered(String emailRaw) {
        String email = nvl(emailRaw);
        if (email.isEmpty()) return false;
        return DB.getSQLValue(null,
            "SELECT COUNT(*) FROM AD_User WHERE IsActive='Y' AND AD_Client_ID=? AND UPPER(TRIM(EMail))=UPPER(TRIM(?))",
            DEFAULT_CLIENT_ID, email) > 0;
    }

    private static String nvl(String s) { return s == null ? "" : s.trim(); }

   
    private int getUserIdByEmail(String emailRaw) {
        String email = nvl(emailRaw);
        if (email.isEmpty()) return 0;
        return DB.getSQLValue(null,
            "SELECT AD_User_ID FROM AD_User " +
            "WHERE IsActive='Y' AND AD_Client_ID=? AND UPPER(TRIM(EMail))=UPPER(TRIM(?))",
            DEFAULT_CLIENT_ID, email);
    }

    private boolean hasRole(int adUserId, int roleId) {
        if (adUserId <= 0) return false;
        int cnt = DB.getSQLValue(null,
            "SELECT COUNT(*) FROM AD_User_Roles WHERE AD_User_ID=? AND AD_Role_ID=? AND IsActive='Y'",
            adUserId, roleId);
        return cnt > 0;
    }
}

