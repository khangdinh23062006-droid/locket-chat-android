package com.fpt.locketchat.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValidationUtilsTest {

    @Test
    public void testValidPhoneNumber() {
        assertTrue(ValidationUtils.isValidPhoneNumber("0901234567"));
        assertTrue(ValidationUtils.isValidPhoneNumber("0389998888"));
        assertTrue(ValidationUtils.isValidPhoneNumber("0912345678"));
    }

    @Test
    public void testInvalidPhoneNumber() {
        assertFalse(ValidationUtils.isValidPhoneNumber("123456")); // Quá ngắn
        assertFalse(ValidationUtils.isValidPhoneNumber("0123456789")); // Đầu số cũ 11 số
        assertFalse(ValidationUtils.isValidPhoneNumber("abcd123456")); // Có chữ cái
        assertFalse(ValidationUtils.isValidPhoneNumber("")); // Chuỗi rỗng
        assertFalse(ValidationUtils.isValidPhoneNumber(null)); // Null
    }

    @Test
    public void testValidPassword() {
        assertTrue(ValidationUtils.isValidPassword("123456"));
        assertTrue(ValidationUtils.isValidPassword("secretPassword!@#"));
    }

    @Test
    public void testInvalidPassword() {
        assertFalse(ValidationUtils.isValidPassword("12345")); // Dưới 6 ký tự
        assertFalse(ValidationUtils.isValidPassword(""));
        assertFalse(ValidationUtils.isValidPassword(null));
    }
}
