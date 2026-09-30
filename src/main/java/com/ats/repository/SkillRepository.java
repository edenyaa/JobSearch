package com.ats.repository;

import com.ats.domain.Skill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {

	List<Skill> findByUserId(Long userId);

	Optional<Skill> findByIdAndUserId(Long id, Long userId);

}
