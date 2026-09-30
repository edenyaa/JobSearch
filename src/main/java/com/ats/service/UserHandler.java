package com.ats.service;

import com.ats.domain.User;
import com.ats.dto.UserDtos.UserResponse;
import com.ats.util.PasswordEncoderUtil;
import org.springframework.stereotype.Component;

/** Pure user business rules (validation, password hashing). No database access. */
@Component
public class UserHandler {

	private static final int MIN_PASSWORD_LENGTH = 7;
	private static final String PASSWORD_RULE_MESSAGE =
			"Password must be at least 7 characters long and include both letters and numbers.";

	public User registerUser(String name, String email, String password) {
		validateName(name);
		validateEmailFormat(email);
		validateNewPassword(password);
		User user = new User();
		user.setName(name.trim());
		user.setEmail(email.trim());
		user.setPasswordHash(PasswordEncoderUtil.encodePassword(password));
		return user;
	}

	public void loginUser(User user, String password) {
		if (user == null || !PasswordEncoderUtil.matches(password, user.getPasswordHash())) {
			throw new UnauthorizedException("Invalid email or password.");
		}
	}

	public void editUsername(User user, String oldUsername, String newUsername) {
		validateUserFound(user);
		validateName(newUsername);
		if (!user.getName().equals(oldUsername)) {
			throw new ValidationException("Old username does not match current username.");
		}
		user.setName(newUsername.trim());
	}

	public void editPassword(User user, String oldPassword, String newPassword) {
		validateUserFound(user);
		validateNewPassword(newPassword);
		if (!PasswordEncoderUtil.matches(oldPassword, user.getPasswordHash())) {
			throw new ValidationException("Old password does not match current password.");
		}
		user.setPasswordHash(PasswordEncoderUtil.encodePassword(newPassword));
	}

	public void editEmail(User user, String oldEmail, String newEmail) {
		validateUserFound(user);
		validateEmailFormat(newEmail);
		if (!user.getEmail().equalsIgnoreCase(oldEmail)) {
			throw new ValidationException("Old email does not match current email.");
		}
		user.setEmail(newEmail.trim());
	}

	public UserResponse mapToDTO(User user) {
		return UserResponse.from(user);
	}

	public void validateUserFound(User user) {
		if (user == null) {
			throw new ValidationException("User not found.");
		}
	}

	public void validateUserDoesNotExist(User user) {
		if (user != null) {
			throw new ConflictException("User with the same email already exists.");
		}
	}

	private void validateName(String name) {
		if (name == null || name.isBlank()) {
			throw new ValidationException("Name must not be blank.");
		}
	}

	private void validateEmailFormat(String email) {
		if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			throw new ValidationException("Invalid email format.");
		}
	}

	private void validateNewPassword(String password) {
		if (password == null
				|| password.length() < MIN_PASSWORD_LENGTH
				|| !password.matches(".*[A-Za-z].*")
				|| !password.matches(".*\\d.*")) {
			throw new ValidationException(PASSWORD_RULE_MESSAGE);
		}
	}

}
