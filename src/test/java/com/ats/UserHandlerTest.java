package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.User;
import com.ats.dto.UserDtos.UserResponse;
import com.ats.service.ConflictException;
import com.ats.service.UnauthorizedException;
import com.ats.service.UserHandler;
import com.ats.service.ValidationException;
import org.junit.jupiter.api.Test;

class UserHandlerTest {

    private final UserHandler handler = new UserHandler();

    private User user() {
        return handler.registerUser("Eden", "eden@example.com", "secret123");
    }

    @Test
    public void GivenValidDetails_WhenRegistering_ThenPasswordIsHashedAndFieldsSet() {
        User user = handler.registerUser("Eden", "eden@example.com", "secret123");

        assertNotEquals("secret123", user.getPasswordHash());
        assertEquals("Eden", user.getName());
        assertEquals("eden@example.com", user.getEmail());
    }

    @Test
    public void GivenNameAndEmailWithSpaces_WhenRegistering_ThenTheyAreTrimmed() {
        User user = handler.registerUser("  Eden ", " eden@example.com ", "secret123");

        assertEquals("Eden", user.getName());
        assertEquals("eden@example.com", user.getEmail());
    }

    @Test
    public void GivenWeakPasswords_WhenRegistering_ThenValidationExceptionIsThrown() {
        assertThrows(ValidationException.class, () -> handler.registerUser("E", "e@x.com", "short1"));
        assertThrows(ValidationException.class, () -> handler.registerUser("E", "e@x.com", "onlyletters"));
        assertThrows(ValidationException.class, () -> handler.registerUser("E", "e@x.com", "12345678"));
        assertThrows(ValidationException.class, () -> handler.registerUser("E", "e@x.com", null));
    }

    @Test
    public void GivenPasswordOfExactlyMinimumLength_WhenRegistering_ThenSucceeds() {
        assertDoesNotThrow(() -> handler.registerUser("E", "e@x.com", "abcdef1"));
    }

    @Test
    public void GivenInvalidEmails_WhenRegistering_ThenValidationExceptionIsThrown() {
        String[] badEmails = { "not-an-email", "a@b", "@x.com", "a b@x.com", "", null };

        for (String bad : badEmails) {
            assertThrows(ValidationException.class, () -> handler.registerUser("E", bad, "secret123"));
        }
    }

    @Test
    public void GivenBlankOrNullName_WhenRegistering_ThenValidationExceptionIsThrown() {
        assertThrows(ValidationException.class, () -> handler.registerUser("  ", "e@x.com", "secret123"));
        assertThrows(ValidationException.class, () -> handler.registerUser(null, "e@x.com", "secret123"));
    }

    @Test
    public void GivenRightPassword_WhenLoggingIn_ThenNoException() {
        User user = user();

        assertDoesNotThrow(() -> handler.loginUser(user, "secret123"));
    }

    @Test
    public void GivenWrongNullPasswordOrNullUser_WhenLoggingIn_ThenUnauthorizedExceptionIsThrown() {
        User user = user();

        assertThrows(UnauthorizedException.class, () -> handler.loginUser(user, "wrong123"));
        assertThrows(UnauthorizedException.class, () -> handler.loginUser(user, null));
        assertThrows(UnauthorizedException.class, () -> handler.loginUser(null, "secret123"));
    }

    @Test
    public void GivenWrongOldPassword_WhenEditingPassword_ThenValidationExceptionIsThrown() {
        User user = user();

        assertThrows(ValidationException.class, () -> handler.editPassword(user, "nope1234", "newpass123"));
    }

    @Test
    public void GivenWeakNewPassword_WhenEditingPassword_ThenValidationExceptionIsThrown() {
        User user = user();

        assertThrows(ValidationException.class, () -> handler.editPassword(user, "secret123", "weak"));
    }

    @Test
    public void GivenRightOldPassword_WhenEditingPassword_ThenOnlyNewPasswordWorks() {
        User user = user();

        handler.editPassword(user, "secret123", "newpass123");

        assertDoesNotThrow(() -> handler.loginUser(user, "newpass123"));
        assertThrows(UnauthorizedException.class, () -> handler.loginUser(user, "secret123"));
    }

    @Test
    public void GivenWrongOldName_WhenEditingUsername_ThenValidationExceptionIsThrown() {
        User user = user();

        assertThrows(ValidationException.class, () -> handler.editUsername(user, "Other", "New"));
    }

    @Test
    public void GivenBlankNewName_WhenEditingUsername_ThenValidationExceptionIsThrown() {
        User user = user();

        assertThrows(ValidationException.class, () -> handler.editUsername(user, "Eden", " "));
    }

    @Test
    public void GivenRightOldName_WhenEditingUsername_ThenNameIsChanged() {
        User user = user();

        handler.editUsername(user, "Eden", "New");

        assertEquals("New", user.getName());
    }

    @Test
    public void GivenWrongOldEmail_WhenEditingEmail_ThenValidationExceptionIsThrown() {
        User user = user();

        assertThrows(ValidationException.class, () -> handler.editEmail(user, "other@x.com", "new@x.com"));
    }

    @Test
    public void GivenInvalidNewEmail_WhenEditingEmail_ThenValidationExceptionIsThrown() {
        User user = user();

        assertThrows(ValidationException.class, () -> handler.editEmail(user, "eden@example.com", "bad"));
    }

    @Test
    public void GivenOldEmailInDifferentCase_WhenEditingEmail_ThenEmailIsChanged() {
        User user = user();

        handler.editEmail(user, "EDEN@example.com", "new@x.com");

        assertEquals("new@x.com", user.getEmail());
    }

    @Test
    public void GivenNullUser_WhenEditingOrValidatingFound_ThenValidationExceptionIsThrown() {
        assertThrows(ValidationException.class, () -> handler.editPassword(null, "a", "b"));
        assertThrows(ValidationException.class, () -> handler.editUsername(null, "a", "b"));
        assertThrows(ValidationException.class, () -> handler.editEmail(null, "a", "b@x.com"));
        assertThrows(ValidationException.class, () -> handler.validateUserFound(null));
    }

    @Test
    public void GivenNoUser_WhenValidatingDoesNotExist_ThenNoException() {
        assertDoesNotThrow(() -> handler.validateUserDoesNotExist(null));
    }

    @Test
    public void GivenExistingUser_WhenValidatingDoesNotExist_ThenConflictExceptionIsThrown() {
        User user = user();

        assertThrows(ConflictException.class, () -> handler.validateUserDoesNotExist(user));
    }

    @Test
    public void GivenUser_WhenMappingToDto_ThenPasswordHashIsNotExposed() {
        User user = user();

        UserResponse dto = handler.mapToDTO(user);

        assertEquals("eden@example.com", dto.email());
        assertEquals("Eden", dto.name());
        assertFalse(dto.hasMainCv());
        assertFalse(dto.toString().contains(user.getPasswordHash()));
    }

}
