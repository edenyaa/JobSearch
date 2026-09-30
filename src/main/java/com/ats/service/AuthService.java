package com.ats.service;

import com.ats.domain.SessionToken;
import com.ats.repository.SessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Creates and validates JWT sessions. The subject of every token is the user id. */
@Service
public class AuthService {

	private final SessionRepository sessions;
	private final SecretKey key;
	private final long expirationMillis;

	public AuthService(SessionRepository sessions,
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-minutes:120}") long expirationMinutes) {
		this.sessions = sessions;
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMillis = expirationMinutes * 60 * 1000;
	}

	public String createSession(Long userId) {
		sessions.deleteByExpirationTimeLessThan(System.currentTimeMillis());
		long expireTime = System.currentTimeMillis() + expirationMillis;
		String token = Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(String.valueOf(userId))
				.issuedAt(new Date())
				.expiration(new Date(expireTime))
				.claim("role", "User")
				.signWith(key)
				.compact();
		sessions.save(new SessionToken(token, userId, expireTime));
		return token;
	}

	/** Returns the user id if the token is correctly signed, not expired and still has a live session. */
	public Optional<Long> validate(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			if (sessions.findByToken(token).isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(Long.parseLong(claims.getSubject()));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	public void invalidateSession(String token) {
		sessions.deleteByToken(token);
	}

	public void invalidateAllSessions(Long userId) {
		sessions.deleteByUserId(userId);
	}

}
