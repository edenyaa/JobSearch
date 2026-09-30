package com.ats.service;

import com.ats.domain.Course;
import com.ats.domain.Skill;
import com.ats.dto.CourseDtos.CourseRequest;
import com.ats.repository.CourseRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CourseService {

	private final CourseRepository courses;
	private final UserService users;
	private final SkillService skills;

	public CourseService(CourseRepository courses, UserService users, SkillService skills) {
		this.courses = courses;
		this.users = users;
		this.skills = skills;
	}

	public Course create(Long userId, CourseRequest req) {
		Course c = new Course();
		c.setUser(users.get(userId));
		apply(c, userId, req);
		return courses.save(c);
	}

	@Transactional(readOnly = true)
	public List<Course> list(Long userId) {
		return courses.findByUserId(userId);
	}

	@Transactional(readOnly = true)
	public Course get(Long userId, Long id) {
		return courses.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Course", id));
	}

	public Course update(Long userId, Long id, CourseRequest req) {
		Course c = get(userId, id);
		apply(c, userId, req);
		return c;
	}

	public void delete(Long userId, Long id) {
		courses.delete(get(userId, id));
	}

	private void apply(Course c, Long userId, CourseRequest req) {
		c.setCourseNumber(req.courseNumber());
		c.setInstitution(req.institution());
		c.setDescription(req.description());
		c.setAssignments(req.assignments() == null ? new ArrayList<>() : new ArrayList<>(req.assignments()));
		var linked = new HashSet<Skill>();
		if (req.skillIds() != null) {
			for (Long skillId : req.skillIds()) {
				linked.add(skills.get(userId, skillId));
			}
		}
		c.setSkills(linked);
	}

}
