package com.ats.dto;

import com.ats.domain.User;
import jakarta.validation.constraints.NotBlank;

/** Format rules (email, password strength) are enforced in UserHandler. */
public final class UserDtos {

	private UserDtos() {
	}

	public record CreateUser(@NotBlank String name, @NotBlank String email, @NotBlank String password) {
	}

	public record Login(@NotBlank String email, @NotBlank String password) {
	}

	public record LoginResponse(String token, UserResponse user) {
	}

	public record ChangeValue(@NotBlank String oldValue, @NotBlank String newValue) {
	}

	public record UserResponse(Long id, String name, String email, String mainCvFileName, boolean hasMainCv) {

		public static UserResponse from(User u) {
			return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getMainCvFileName(), u.getMainCv() != null);
		}
	}

}
