package org.adempiere.webui.panel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
 * RegistrationWindow - pure Java ZK Window that handles:
 * - User self-registration
 * - Email OTP (via R_MailText template)
 * - Welcome / temp-password email (via R_MailText template)
 * Validation errors are thrown as IllegalArgumentException with Msg.getMsg(...)
 */
public class RegistrationWindow extends Window implements org.zkoss.zk.ui.event.EventListener<Event> {

    private static final long serialVersionUID = 1L;
    private static final CLogger log = CLogger.getCLogger(RegistrationWindow.class);

    // ---- Mail template names (R_MailText.Name) ----
    private static final String OTP_MAIL_TEXT_NAME     = "REGISTRATION_OTP";
    private static final String WELCOME_MAIL_TEXT_NAME = "REGISTRATION_WELCOME";

    // ---- Defaults: change to your IDs ----
    private static final int DEFAULT_CLIENT_ID = 1000000; // <-- set your AD_Client_ID
    private static final int DEFAULT_ROLE_ID   = 1000023; // <-- set a default AD_Role_ID

    // ---- UI ----
    private Textbox txtName;
    private Textbox txtIDNo;
    private Textbox txtPassportNo;
    private Textbox txtCellNo;
    private Textbox txtEmail;
    private Textbox txtOtp;
    private Button  btnSendOtp;
    private Button  btnRegisterUser;

    public RegistrationWindow() {
        setTitle(Msg.getMsg(Env.getCtx(), "UserRegistrationTitle")); // AD_Message
        setWidth("700px");
        setClosable(true);
        setSizable(false);
        setBorder("normal");
        setId("registrationWindow");

        buildUI();
        wireEvents();
    }
    
   
    
    private void buildUI() {
        Vbox form = new Vbox();
        form.setSpacing("8px");
        // Do NOT set hflex on form when you use explicit widths on children
        form.setWidth("100%");           // fine to let the form fill the window

        // NAME (large)
        txtName = new Textbox();
        txtName.setPlaceholder(Msg.getMsg(Env.getCtx(), "FullName"));
        txtName.setWidth("600px");       // large, but not full width
        form.appendChild(txtName);

        // ID NUMBER (smaller, on its own line)
        txtIDNo = new Textbox();
        txtIDNo.setPlaceholder(Msg.getMsg(Env.getCtx(), "IDNumber"));
        txtIDNo.setMaxlength(13);
        txtIDNo.setWidth("300px");
        form.appendChild(txtIDNo);

        // PASSPORT NUMBER (smaller, stacked under ID)
        txtPassportNo = new Textbox();
        txtPassportNo.setPlaceholder(Msg.getMsg(Env.getCtx(), "PassportNumber"));
        txtPassportNo.setWidth("300px");
        form.appendChild(txtPassportNo);

        // CELL NUMBER (smaller)
        txtCellNo = new Textbox();
        txtCellNo.setPlaceholder(Msg.getMsg(Env.getCtx(), "CellNumber"));
        txtCellNo.setMaxlength(10);
        txtCellNo.setWidth("300px");
        form.appendChild(txtCellNo);

        // EMAIL (large)
        txtEmail = new Textbox();
        txtEmail.setPlaceholder(Msg.getMsg(Env.getCtx(), "Email"));
        txtEmail.setWidth("600px");
        form.appendChild(txtEmail);

        // SEND OTP (button), then OTP BELOW it (smaller)
        btnSendOtp = new Button(Msg.getMsg(Env.getCtx(), "SendOtp"));
        
        
     // optional: keep disabled until validated
        btnSendOtp.setDisabled(true);
        form.appendChild(btnSendOtp);

        txtOtp = new Textbox();
        txtOtp.setPlaceholder(Msg.getMsg(Env.getCtx(), "EnterOTP"));
        txtOtp.setWidth("220px");        // smaller than other fields
        form.appendChild(txtOtp);

        // REGISTER
        btnRegisterUser = new Button(Msg.getMsg(Env.getCtx(), "RegisterMe"));
        btnRegisterUser.setDisabled(true); 
        form.appendChild(btnRegisterUser);

        this.appendChild(form);
    }



    /** Wire listeners */
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
                    validateIdNo(txtIDNo, null);                 // throws WrongValueException if invalid
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

    // ----------------------- OTP SEND -----------------------

    private void onSendOtp() {
        String email = nvl(txtEmail.getValue());
        if (email.isEmpty())
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "FillEmailFirst"));

        // All core fields must be valid before requesting an OTP
        if (!isCoreFieldsValid()) {
            String msg = Msg.getMsg(Env.getCtx(), "CompleteFieldsBeforeOTP");
            if (msg == null || "CompleteFieldsBeforeOTP".equals(msg)) {
                msg = "Please complete all required fields (Name, ID/Passport, Mobile, valid Email) before requesting an OTP.";
            }
            throw new IllegalArgumentException(msg);
        }

        if (isEmailRegistered(email)) {
            String msg = Msg.getMsg(Env.getCtx(), "EmailAlreadyRegistered");
            if (msg == null || "EmailAlreadyRegistered".equals(msg)) {
                msg = "This email is already registered. Please sign in or use Forgot Password.";
            }
            throw new IllegalArgumentException(msg);
        }

        // generate + store OTP in session (or switch to DB if you need multi-device)
        String otp = String.valueOf((int)(Math.random() * 900000) + 100000);
        Executions.getCurrent().getSession().setAttribute("OTP_CODE", otp);

        Map<String,String> vars = new HashMap<>();
        vars.put("OTP", otp);
        vars.put("EMail", email);
        vars.put("FullName", nvl(txtName.getValue()));

        boolean ok = sendWithTemplate(email, OTP_MAIL_TEXT_NAME, vars, null);
        if (!ok)
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "OtpSendFailed"));

        // Optional success info (not an error)
        FDialog.info(0, this, Msg.getMsg(Env.getCtx(), "OtpSent", new Object[]{ email }));
    }

    // ----------------------- REGISTER -----------------------

    private void onRegister() {
        String name       = nvl(txtName.getValue());
        String idNo       = nvl(txtIDNo.getValue());
        String passportNo = nvl(txtPassportNo.getValue());
        String cellNo     = nvl(txtCellNo.getValue());
        String email      = nvl(txtEmail.getValue());
        String otp        = nvl(txtOtp.getValue());

        // Required (except ID/Passport special rule)
        if (name.isEmpty() || cellNo.isEmpty() || email.isEmpty() || otp.isEmpty()) {
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "FillRequiredFields"));
        }
        validateCellNo();
        // Exactly one of ID or Passport
        if (idNo.isEmpty() && passportNo.isEmpty()) {
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "EnterIdOrPassport"));
        }
        if (!idNo.isEmpty() && !passportNo.isEmpty()) {
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "OnlyOneIdOrPassport"));
        }
        // ID must be 13 digits if provided
        if (!idNo.isEmpty() && !idNo.matches("\\d{13}")) {
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "InvalidIdNumber"));
        }
        
        if (!idNo.isEmpty()) {
            validateIdNo(txtIDNo, null); // throws on invalid
        }
        
     // Redundant safety: block duplicate emails at creation time too
        if (isEmailRegistered(email)) {
            String msg = Msg.getMsg(Env.getCtx(), "EmailAlreadyRegistered");
            if (msg == null || "EmailAlreadyRegistered".equals(msg)) {
                msg = "This email is already registered. Please sign in or use Forgot Password.";
            }
            throw new IllegalArgumentException(msg);
        }
        // OTP check
        String storedOtp = (String) Executions.getCurrent().getSession().getAttribute("OTP_CODE");
        if (storedOtp == null || !storedOtp.equals(otp)) {
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "InvalidOtp"));
        }

        // ---- Create User ----
        MUser user = new MUser(Env.getCtx(), 0, null);
        user.setName(name);
        user.setPhone(cellNo);
        user.setEMail(email);
        user.setIsActive(true);
        user.set_ValueNoCheck(MUser.COLUMNNAME_AD_Client_ID, DEFAULT_CLIENT_ID);

        // Custom columns (adjust names to match your dictionary)
        user.set_ValueOfColumn("ZZ_ID_Passport_No", idNo);
        user.set_ValueOfColumn("ZZ_Passport_No",   passportNo);

        // Temp password & force change
        //String tempPwd = UUID.randomUUID().toString().substring(0, 8);
        String tempPwd = PasswordGenerator.generatePassword(8);
        user.setPassword(tempPwd);
        user.setIsExpired(true);
        user.saveEx();

        // ---- Link default role ----
        MUserRoles ur = new MUserRoles(Env.getCtx(), 0, null);
        ur.setAD_User_ID(user.getAD_User_ID());
        ur.setAD_Role_ID(DEFAULT_ROLE_ID);
        ur.setIsActive(true);
        ur.set_ValueNoCheck(MUserRoles.COLUMNNAME_AD_Client_ID, DEFAULT_CLIENT_ID);
        ur.saveEx();

        // ---- Welcome email via template ----
        Map<String,String> vars = new HashMap<>();
        vars.put("FullName",    user.getName());
        vars.put("TempPassword", tempPwd);
        vars.put("EMail",       user.getEMail());

        boolean ok = sendWithTemplate(user.getEMail(), WELCOME_MAIL_TEXT_NAME, vars, user);
        if (!ok) {
            // Decide policy: warn (don’t block registration) or throw
            log.warning("Welcome email failed to send to " + user.getEMail());
        }

        // Optional success info (not an error)
        FDialog.info(0, this, Msg.getMsg(Env.getCtx(), "RegistrationSuccess"));
        detach();
    }

    // ----------------------- Mail helper using R_MailText -----------------------

    /**
     * Send an email using an R_MailText template.
     * Variables are provided via ctxVars and available as #Key# in the template body.
     * If userOrNull != null, mailText.setUser(user) is applied for @User@ tokens.
     */
    private boolean sendWithTemplate(String toEMail, String templateName,
                                     Map<String, String> ctxVars,
                                     MUser userOrNull) {
        int clientId = userOrNull != null ? userOrNull.getAD_Client_ID() : Env.getAD_Client_ID(Env.getCtx());
        int mailTextId = findMailTextId(templateName, clientId);
        if (mailTextId <= 0)
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "MailTextNotFound"));

        MMailText mailText = new MMailText(Env.getCtx(), mailTextId, null);
        mailText.setLanguage(Env.getContext(Env.getCtx(), Env.LANGUAGE));
        if (userOrNull != null) {
            mailText.setUser(userOrNull);
        }

        // Push variables into context  as #Key
        if (ctxVars != null) {
            for (Map.Entry<String, String> e : ctxVars.entrySet()) {
                Env.setContext(Env.getCtx(), "#" + e.getKey(), safeTrim(e.getValue()));
            }
        }

        String body = mailText.getMailText(true, true, true);
        body = Env.parseVariable(body, userOrNull, null, true);

        // Clean up context variables 
        if (ctxVars != null) {
            for (String k : ctxVars.keySet()) {
                Env.setContext(Env.getCtx(), "#" + k, "");
            }
        }

        MClient client = MClient.get(Env.getCtx());
        EMail email = client.createEMail(toEMail, mailText.getMailHeader(), body, mailText.isHtml());
        if (mailText.isHtml()) {
            email.setMessageHTML(mailText.getMailHeader(), body);
        } else {
            email.setSubject(mailText.getMailHeader());
            email.setMessageText(body);
        }

        if (!email.isValid() && !email.isValid(true))
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "EmailNotValid"));

        return EMail.SENT_OK.equals(email.send());
    }

    /** Find R_MailText_ID by Name for client or system */
    private int findMailTextId(String name, int adClientId) {
        String sql =
            "SELECT R_MailText_ID " +
            "FROM R_MailText " +
            "WHERE IsActive='Y' AND Name=? " +
            "AND AD_Client_ID IN (?, 0) " +
            "ORDER BY AD_Client_ID";
        return DB.getSQLValue(null, sql, name, adClientId);
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }
    
    
    //
    
 // ---------- Non-throwing checks used for button enable/disable ----------

    private boolean isNameValid() {
        return !nvl(txtName.getValue()).isEmpty();
    }

    private boolean isCellValid() {
        return nvl(txtCellNo.getValue()).matches("\\d{10}");
    }

    private boolean isEmailValid() {
        String email = safeTrim(txtEmail.getValue());
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return false;
        // Only treat as valid if not already registered
        return !isEmailRegistered(email);
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

    private boolean isOtpEntered() {
        // Enable Register only when OTP looks like a 6-digit code
        return nvl(txtOtp.getValue()).matches("\\d{6}");
    }

    private boolean isCoreFieldsValid() {
        return isNameValid() && isIdOrPassportValid() && isCellValid() && isEmailValid();
    }

    // Central controller: enable/disable buttons based on current values
    private void updateButtonsState() {
        boolean coreValid = isCoreFieldsValid();
        btnSendOtp.setDisabled(!coreValid);

        boolean otpOk = isOtpEntered();
        btnRegisterUser.setDisabled(!(coreValid && otpOk));
    }

    // 
    
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
    
    public static void validateIdNo(Textbox txtIDNo, String idValue) {
    	String id = null;
    	if (txtIDNo != null) {
    		id = nvl(txtIDNo.getValue());
    	}else {
    		id = nvl(idValue);
    	}
        

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
        String email = safeTrim(emailRaw);
        if (email.isEmpty()) return false;

        // Case/space-insensitive match within your client
        final String sql =
            "SELECT COUNT(*) " +
            "FROM AD_User " +
            "WHERE IsActive='Y' " +
            "  AND AD_Client_ID=? " +
            "  AND UPPER(TRIM(EMail))=UPPER(TRIM(?))";

        int cnt = DB.getSQLValue(null, sql, DEFAULT_CLIENT_ID, email);
        return cnt > 0;
    }
    
    
    
    private void validateEmailOnBlur() {
        String email = safeTrim(txtEmail.getValue());

        // Empty: clear error + disable buttons via normal logic
        if (email.isEmpty()) {
            clearWrongValue(txtEmail);
            updateButtonsState();   // will disable Register because email is not valid
            return;
        }

        // Invalid format
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            String msg = Msg.getMsg(Env.getCtx(), "InvalidEMail");
            if (msg == null || "InvalidEMail".equals(msg)) {
                msg = "Please enter a valid email address.";
            }

            // explicitly disable actions before we throw
            btnSendOtp.setDisabled(true);
            btnRegisterUser.setDisabled(true);
            throw new WrongValueException(txtEmail, msg);
        }

        // Email already registered
        if (isEmailRegistered(email)) {
            String msg = Msg.getMsg(Env.getCtx(), "EmailAlreadyRegistered");
            if (msg == null || "EmailAlreadyRegistered".equals(msg)) {
                msg = "This email is already registered. Please sign in or use Forgot Password.";
            }

            // IMPORTANT: disable Register and Send OTP BEFORE raising the error
            btnSendOtp.setDisabled(true);
            btnRegisterUser.setDisabled(true);
            throw new WrongValueException(txtEmail, msg);
        }

        // Valid and not registered
        clearWrongValue(txtEmail);
        updateButtonsState();
    }



    // Utility: clear prior WrongValue state (if any)
    private void clearWrongValue(Component comp) {
        // ZK clears the red box automatically when the value changes,
        // but this ensures we remove any lingering state before enabling OTP.
        // No-op placeholder in case your framework skin needs a touch here.
    }



    /** Helper to show modally and handle attachment correctly */
    public static void show(Component attachTo) {
        RegistrationWindow w = new RegistrationWindow();
        if (attachTo != null && attachTo.getPage() != null) {
            attachTo.appendChild(w);      // attach first
            w.setMode(Window.MODAL);      // then modal
        } else {
            // fallback: attach to the first page in the desktop
            Page p = Executions.getCurrent().getDesktop().getPages().iterator().next();
            w.setPage(p);                 // attach to page
            w.setMode(Window.MODAL);      // then modal
        }
    }
    
    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}

