package com.ats.service;

import com.ats.domain.Course;
import com.ats.domain.Skill;
import com.ats.domain.SkillSource;
import com.ats.dto.SkillDtos.SkillRequest;
import com.ats.repository.CourseRepository;
import com.ats.repository.SkillRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SkillService {

	private final SkillRepository skills;
	private final CourseRepository courses;
	private final UserService users;

	public SkillService(SkillRepository skills, CourseRepository courses, UserService users) {
		this.skills = skills;
		this.courses = courses;
		this.users = users;
	}

	public Skill create(Long userId, SkillRequest req) {
		Skill s = new Skill();
		s.setUser(users.get(userId));
		apply(s, req);
		return skills.save(s);
	}

	public Skill createFromCv(Long userId, String name, List<String> keywords) {
		Skill s = new Skill();
		s.setUser(users.get(userId));
		s.setName(name);
		s.setKeywords(new ArrayList<>(keywords));
		s.setSource(SkillSource.CV);
		return skills.save(s);
	}

	@Transactional(readOnly = true)
	public List<Skill> list(Long userId) {
		return skills.findByUserId(userId);
	}

	@Transactional(readOnly = true)
	public Skill get(Long userId, Long id) {
		return skills.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Skill", id));
	}

	public Skill update(Long userId, Long id, SkillRequest req) {
		Skill s = get(userId, id);
		apply(s, req);
		return s;
	}

	public void delete(Long userId, Long id) {
		Skill s = get(userId, id);
		for (Course c : courses.findByUserIdAndSkillsId(userId, id)) {
			c.getSkills().remove(s);
		}
		skills.delete(s);
	}

	private void apply(Skill s, SkillRequest req) {
		s.setName(req.name());
		s.setExplanation(req.explanation());
		s.setKeywords(req.keywords() == null ? new ArrayList<>() : new ArrayList<>(req.keywords()));
	}

}
