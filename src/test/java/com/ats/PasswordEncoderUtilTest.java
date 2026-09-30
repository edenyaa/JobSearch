package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.util.PasswordEncoderUtil;
import org.junit.jupiter.api.Test;

class PasswordEncoderUtilTest {

    @Test
    public void GivenRawPassword_WhenEncoding_ThenResultIsBcryptAndNotPlaintext() {
        String raw = "secret123";

        String encoded = PasswordEncoderUtil.encodePassword(raw);

        assertNotEquals(raw, encoded);
        assertTrue(encoded.startsWith("$2"));
    }

    @Test
    public void GivenSamePasswordTwice_WhenEncoding_ThenHashesDiffer() {
        String first = PasswordEncoderUtil.encodePassword("secret123");
        String second = PasswordEncoderUtil.encodePassword("secret123");

        assertNotEquals(first, second);
    }

    @Test
    public void GivenEncodedPassword_WhenMatchingRightPassword_ThenTrue() {
        String encoded = PasswordEncoderUtil.encodePassword("secret123");

        assertTrue(PasswordEncoderUtil.matches("secret123", encoded));
    }

    @Test
    public void GivenEncodedPassword_WhenMatchingWrongOrNullPassword_ThenFalse() {
        String encoded = PasswordEncoderUtil.encodePassword("secret123");

        assertFalse(PasswordEncoderUtil.matches("Secret123", encoded));
        assertFalse(PasswordEncoderUtil.matches(null, encoded));
    }

}
