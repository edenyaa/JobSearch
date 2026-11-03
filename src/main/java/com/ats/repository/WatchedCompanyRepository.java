package com.ats.repository;

import com.ats.domain.WatchedCompany;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchedCompanyRepository extends JpaRepository<WatchedCompany, Long> {

	List<WatchedCompany> findByUserIdOrderByNameAsc(Long userId);

	Optional<WatchedCompany> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndBoardAndSlug(Long userId, String board, String slug);

}
