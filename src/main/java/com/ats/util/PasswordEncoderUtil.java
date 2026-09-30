package com.ats.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Static helper around BCrypt: only the encoded (hashed) password is ever stored. */
public final class PasswordEncoderUtil {

	private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

	private PasswordEncoderUtil() {
	}

	public static String encodePassword(String rawPassword) {
		return ENCODER.encode(rawPassword);
	}

	public static boolean matches(String rawPassword, String encodedPassword) {
		return rawPassword != null && ENCODER.matches(rawPassword, encodedPassword);
	}

}
