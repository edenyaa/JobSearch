package com.ats.repository;

import com.ats.domain.Job;
import com.ats.domain.JobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

	List<Job> findByUserIdOrderByAppliedDateDesc(Long userId);

	List<Job> findByUserIdAndStatusOrderByAppliedDateDesc(Long userId, JobStatus status);

	Optional<Job> findByIdAndUserId(Long id, Long userId);

}
