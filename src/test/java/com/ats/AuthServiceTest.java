package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.repository.SessionRepository;
import com.ats.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthServiceTest {

    private static final String SECRET = "dev-only-secret-change-me-dev-only-secret";

    @Autowired
    AuthService auth;

    @Autowired
    SessionRepository sessions;

    @Test
    public void GivenCreatedSession_WhenValidating_ThenReturnsItsUserAndRowExists() {
        String token = auth.createSession(42L);

        assertEquals(42L, auth.validate(token).orElseThrow());
        assertTrue(sessions.findByToken(token).isPresent());
    }

    @Test
    public void GivenInvalidatedSession_WhenValidating_ThenEmpty() {
        String token = auth.createSession(43L);

        auth.invalidateSession(token);

        assertTrue(auth.validate(token).isEmpty());
    }

    @Test
    public void GivenSeveralSessions_WhenInvalidatingAllOfOneUser_ThenOnlyThatUsersSessionsAreRemoved() {
        String first = auth.createSession(44L);
        String second = auth.createSession(44L);
        String other = auth.createSession(45L);

        auth.invalidateAllSessions(44L);

        assertTrue(auth.validate(first).isEmpty());
        assertTrue(auth.validate(second).isEmpty());
        assertTrue(auth.validate(other).isPresent());
    }

    @Test
    public void GivenNullBlankOrGarbageToken_WhenValidating_ThenEmpty() {
        assertTrue(auth.validate(null).isEmpty());
        assertTrue(auth.validate("").isEmpty());
        assertTrue(auth.validate("not.a.jwt").isEmpty());
    }

    @Test
    public void GivenTamperedToken_WhenValidating_ThenEmpty() {
        String token = auth.createSession(46L);
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertTrue(auth.validate(tampered).isEmpty());
    }

    @Test
    public void GivenTokenSignedWithAnotherKey_WhenValidating_ThenEmptyEvenIfStored() {
        AuthService foreign = new AuthService(sessions, "a-completely-different-secret-key-1234567890", 60);
        String token = foreign.createSession(47L);

        assertTrue(auth.validate(token).isEmpty());
    }

    @Test
    public void GivenExpiredToken_WhenValidating_ThenEmpty() {
        AuthService shortLived = new AuthService(sessions, SECRET, -1);
        String token = shortLived.createSession(48L);

        assertTrue(auth.validate(token).isEmpty());
    }

    @Test
    public void GivenValidSignatureButDeletedSessionRow_WhenValidating_ThenEmpty() {
        String token = auth.createSession(49L);
        sessions.deleteByToken(token);

        assertTrue(auth.validate(token).isEmpty());
    }

    @Test
    public void GivenExpiredSessionsInDb_WhenCreatingNewSession_ThenExpiredRowsAreRemoved() {
        AuthService shortLived = new AuthService(sessions, SECRET, -1);
        String expired = shortLived.createSession(50L);

        auth.createSession(51L);

        assertTrue(sessions.findByToken(expired).isEmpty());
    }

}
