package com.ats.repository;

import com.ats.domain.Job;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

	List<Job> findByUserIdOrderByAppliedDateDesc(Long userId);

	Optional<Job> findByIdAndUserId(Long id, Long userId);

}
