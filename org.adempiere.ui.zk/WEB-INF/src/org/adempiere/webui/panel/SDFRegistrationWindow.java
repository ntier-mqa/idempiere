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
 * SDFRegistrationWindow
 * ----------------------
 * A separate, self-contained registration window that assigns the SDF role.
 * It DOES NOT modify or depend on the original RegistrationWindow code.
 *
 * Logic:
 *  - If email exists: assign SDF role to existing user (no OTP).
 *  - If email is new: OTP -> create user -> assign SDF role.
 */
public class SDFRegistrationWindow extends Window implements org.zkoss.zk.ui.event.EventListener<Event> {

    private static final long serialVersionUID = 1L;
    private static final CLogger log = CLogger.getCLogger(SDFRegistrationWindow.class);

    // Mail templates (R_MailText.Name)
    private static final String OTP_MAIL_TEXT_NAME     = "REGISTRATION_OTP";
    public static final String WELCOME_MAIL_TEXT_NAME = "REGISTRATION_WELCOME";

    // Configure for your instance
    private static final int DEFAULT_CLIENT_ID = 1000000; // <-- your AD_Client_ID
    private static final int SDF_ROLE_ID       = 1000042; // <-- your SDF AD_Role_ID

    // UI
    private Textbox txtName;
    private Textbox txtIDNo;
    private Textbox txtPassportNo;
    private Textbox txtCellNo;
    private Textbox txtEmail;
    private Textbox txtOtp;
    private Button  btnSendOtp;
    private Button  btnRegisterUser;

    public SDFRegistrationWindow() {
        setTitle("Register SDF User");
        setWidth("700px");
        setClosable(true);
        setSizable(false);
        setBorder("normal");
        setId("sdfRegistrationWindow");
        buildUI();
        wireEvents();
    }

    public static void show(Component attachTo) {
        SDFRegistrationWindow w = new SDFRegistrationWindow();
        if (attachTo != null && attachTo.getPage() != null) {
            attachTo.appendChild(w);
            w.setMode(Window.MODAL);
        } else {
            Page p = Executions.getCurrent().getDesktop().getPages().iterator().next();
            w.setPage(p);
            w.setMode(Window.MODAL);
        }
    }

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
        txtIDNo.addEventListener(Events.ON_CHANGING, ev -> {
            String v = nvl(((InputEvent)ev).getValue());
            txtPassportNo.setDisabled(!v.isEmpty());
            updateButtonsState();
        });
        txtPassportNo.addEventListener(Events.ON_CHANGING, ev -> {
            String v = nvl(((InputEvent)ev).getValue());
            txtIDNo.setDisabled(!v.isEmpty());
            updateButtonsState();
        });
        txtName.addEventListener(Events.ON_CHANGE, ev -> updateButtonsState());
        txtCellNo.addEventListener(Events.ON_CHANGE, ev -> { try { validateCellNo(); } finally { updateButtonsState(); } });
        txtEmail.addEventListener(Events.ON_CHANGE, ev -> { try { validateEmailOnBlur(); } finally { updateButtonsState(); } });
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

    // ---------------- OTP SEND ----------------
    private void onSendOtp() {
        String email = nvl(txtEmail.getValue());
        if (email.isEmpty())
            throw new IllegalArgumentException(Msg.getMsg(Env.getCtx(), "FillEmailFirst"));

        if (!isCoreFieldsValid())
            throw new IllegalArgumentException("Please complete all required fields before requesting an OTP.");

        if (isEmailRegistered(email))
            throw new IllegalArgumentException("This email already exists. Use Register to assign the SDF role.");

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

    // --------------- REGISTER -----------------
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

        
        if (exists) {
            // Existing user path: check if SDF role already present
            int adUserId = getUserIdByEmail(email);
            if (adUserId <= 0) {
                throw new IllegalArgumentException("Unable to find existing user.");
            }

            if (hasRole(adUserId, SDF_ROLE_ID)) {
                // Already has SDF role → just inform the user, no changes.
                FDialog.info(0, this, "SDF role already exists for this user.");
                detach();
                return;
            }

            // Does not have SDF role yet → assign role (and update optional fields)
            assignSdfToExistingUser(email, cellNo, idNo, passportNo);
            FDialog.info(0, this, Msg.getMsg(Env.getCtx(), "SDFRoleAssigned"));
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
        user.setPassword(generatePassword(8));
        user.setIsExpired(true);
        user.saveEx();

        // Assign SDF role
        MUserRoles ur = new MUserRoles(Env.getCtx(), 0, null);
        ur.setAD_User_ID(user.getAD_User_ID());
        ur.setAD_Role_ID(SDF_ROLE_ID);
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

    // --------------- Helpers ------------------
    private void assignSdfToExistingUser(String email, String cellNo, String idNo, String passportNo) {
        int adUserId = DB.getSQLValue(null,
            "SELECT AD_User_ID FROM AD_User WHERE IsActive='Y' AND AD_Client_ID=? AND UPPER(TRIM(EMail))=UPPER(TRIM(?))",
            DEFAULT_CLIENT_ID, email);
        if (adUserId <= 0)
            throw new IllegalArgumentException("Unable to find existing user.");

        int has = DB.getSQLValue(null,
            "SELECT COUNT(*) FROM AD_User_Roles WHERE AD_User_ID=? AND AD_Role_ID=? AND IsActive='Y'",
            adUserId, SDF_ROLE_ID);
        if (has == 0) {
            MUserRoles ur = new MUserRoles(Env.getCtx(), 0, null);
            ur.setAD_User_ID(adUserId);
            ur.setAD_Role_ID(SDF_ROLE_ID);
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

    public static boolean sendWithTemplate(String toEMail, String templateName, Map<String,String> ctxVars, MUser userOrNull) {
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
    private boolean isEmailValid(){ return nvl(txtEmail.getValue()).matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"); }
    private boolean isIdOrPassportValid() {
        String id = nvl(txtIDNo.getValue());
        String pass = nvl(txtPassportNo.getValue());
        if (id.isEmpty() && pass.isEmpty()) return false;
        if (!id.isEmpty() && !pass.isEmpty()) return false;
        if (!id.isEmpty() && !id.matches("\\d{13}")) return false;
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
     // NEW: lock/unlock OTP field based on whether the email already exists
        if (emailExists) {
            txtOtp.setReadonly(true);
            txtOtp.setValue("");           // optional: clear any stray code
        } else {
            txtOtp.setReadonly(false);
        }
        btnSendOtp.setDisabled(!coreValid || emailExists);
        boolean allowRegister = coreValid && (emailExists || isOtpEntered());
        btnRegisterUser.setDisabled(!allowRegister);
        
    }

    private void validateCellNo() {
        String cell = nvl(txtCellNo.getValue());
        if (!cell.matches("\\d{10}"))
            throw new WrongValueException(txtCellNo, "Mobile number must be exactly 10 digits.");
    }

    private void validateEmailOnBlur() {
        String email = nvl(txtEmail.getValue());
        if (email.isEmpty()) return;
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new WrongValueException(txtEmail, "Please enter a valid email address.");
    }

    private boolean isEmailRegistered(String emailRaw) {
        String email = nvl(emailRaw);
        if (email.isEmpty()) return false;
        return DB.getSQLValue(null,
            "SELECT COUNT(*) FROM AD_User WHERE IsActive='Y' AND AD_Client_ID=? AND UPPER(TRIM(EMail))=UPPER(TRIM(?))",
            DEFAULT_CLIENT_ID, email) > 0;
    }

    private static String nvl(String s) { return s == null ? "" : s.trim(); }

    public static String generatePassword(int len) {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
        StringBuilder sb = new StringBuilder(len);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
    
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
