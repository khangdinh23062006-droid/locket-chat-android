package com.fpt.locketchat.utils;

import java.util.regex.Pattern;

public class ValidationUtils {
    // Regex định dạng số điện thoại Việt Nam (10 chữ số, bắt đầu bằng 03, 05, 07, 08, 09)
    private static final Pattern VIETNAM_PHONE_PATTERN = Pattern.compile("^(03|05|07|08|09)[0-9]{8}$");

    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return VIETNAM_PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        return password.trim().length() >= 6;
    }

    public static boolean isNotEmpty(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
