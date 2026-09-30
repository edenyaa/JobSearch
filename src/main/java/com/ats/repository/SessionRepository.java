package com.ats.repository;

import com.ats.domain.SessionToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface SessionRepository extends JpaRepository<SessionToken, String> {

	Optional<SessionToken> findByToken(String token);

	@Transactional
	void deleteByToken(String token);

	@Transactional
	void deleteByUserId(Long userId);

	@Transactional
	void deleteByExpirationTimeLessThan(long time);

}
