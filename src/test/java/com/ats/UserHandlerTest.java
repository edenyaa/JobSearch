package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.User;
import com.ats.service.UnauthorizedException;
import com.ats.service.UserHandler;
import com.ats.service.ValidationException;
import org.junit.jupiter.api.Test;

class UserHandlerTest {

	private final UserHandler handler = new UserHandler();

	@Test
	void registerHashesPassword() {
		User u = handler.registerUser("Eden", "eden@example.com", "secret123");
		assertNotEquals("secret123", u.getPasswordHash());
		handler.loginUser(u, "secret123");
		assertThrows(UnauthorizedException.class, () -> handler.loginUser(u, "wrong123"));
	}

	@Test
	void rejectsWeakPasswordAndBadEmail() {
		assertThrows(ValidationException.class, () -> handler.registerUser("E", "e@x.com", "short1"));
		assertThrows(ValidationException.class, () -> handler.registerUser("E", "e@x.com", "onlyletters"));
		assertThrows(ValidationException.class, () -> handler.registerUser("E", "not-an-email", "secret123"));
	}

	@Test
	void editPasswordRequiresOldPassword() {
		User u = handler.registerUser("Eden", "eden@example.com", "secret123");
		assertThrows(ValidationException.class, () -> handler.editPassword(u, "nope1234", "newpass123"));
		handler.editPassword(u, "secret123", "newpass123");
		handler.loginUser(u, "newpass123");
	}

}
