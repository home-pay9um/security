package kr.kro.pay9um.core.common.util;

import java.security.SecureRandom;

public class CustomUID {
    private static final String ALPHA_NUMERIC = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 16;

    private CustomUID() {
    }

    public static String randomUID() {
        return randomUID(DEFAULT_LENGTH);
    }

    public static String randomUID(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(ALPHA_NUMERIC.length());
            sb.append(ALPHA_NUMERIC.charAt(randomIndex));
        }
        return sb.toString();
    }
}
