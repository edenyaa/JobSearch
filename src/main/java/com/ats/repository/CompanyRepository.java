package com.ats.repository;

import com.ats.domain.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

	List<Company> findByUserIdOrderByNameAsc(Long userId);

	Optional<Company> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

	boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

}
