package com.ats.repository;

import com.ats.domain.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

	List<Course> findByUserId(Long userId);

	Optional<Course> findByIdAndUserId(Long id, Long userId);

	List<Course> findByUserIdAndSkillsId(Long userId, Long skillId);

}
