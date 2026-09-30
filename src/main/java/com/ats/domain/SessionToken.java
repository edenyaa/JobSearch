package com.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A login session: the JWT is only accepted while its row exists (so logout works immediately). */
@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor
public class SessionToken {

	@Id
	@Column(length = 1024)
	private String token;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private long expirationTime;

	public SessionToken(String token, Long userId, long expirationTime) {
		this.token = token;
		this.userId = userId;
		this.expirationTime = expirationTime;
	}

}
