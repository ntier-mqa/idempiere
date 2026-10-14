package org.adempiere.webui.panel;

import java.security.SecureRandom;
import java.util.UUID;

import org.compiere.model.MPasswordRule;

public class PasswordGenerator {
    private static final String UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER   = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS  = "0123456789";
    // Adjust this set to your policy if needed:
    private static final String ALL     = UPPER + LOWER + DIGITS + MPasswordRule.SPECIAL;

    private static final SecureRandom RNG = new SecureRandom();

    public static String generatePassword(int length) {
        if (length < 3) throw new IllegalArgumentException("length must be ≥ 3");

        StringBuilder sb = new StringBuilder(length);
        // Ensure required character classes
        sb.append(randomChar(UPPER));
        sb.append(randomChar(DIGITS));
        sb.append(randomChar(MPasswordRule.SPECIAL));

        // Fill the rest from the full set
        for (int i = 3; i < length; i++) sb.append(randomChar(ALL));

        // Shuffle so first three aren’t predictable
        return shuffle(sb.toString());
    }

    private static char randomChar(String alphabet) {
        return alphabet.charAt(RNG.nextInt(alphabet.length()));
    }

    private static String shuffle(String s) {
        char[] a = s.toCharArray();
        for (int i = a.length - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            char t = a[i]; a[i] = a[j]; a[j] = t;
        }
        return new String(a);
    }

    public static void main(String[] args) {
        String tempPwd = generatePassword(8);
        System.out.println(tempPwd);
    }
}
