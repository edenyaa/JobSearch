package com.ats.dto;

import com.ats.domain.Course;
import com.ats.domain.Skill;
import jakarta.validation.constraints.NotBlank;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class CourseDtos {

	private CourseDtos() {
	}

	public record CourseRequest(
			@NotBlank String courseNumber,
			String institution,
			String description,
			List<String> assignments,
			Set<Long> skillIds) {
	}

	public record CourseResponse(
			Long id,
			String courseNumber,
			String institution,
			String description,
			List<String> assignments,
			List<Long> skillIds) {

		public static CourseResponse from(Course c) {
			return new CourseResponse(c.getId(), c.getCourseNumber(), c.getInstitution(), c.getDescription(),
					List.copyOf(c.getAssignments()),
					c.getSkills().stream().map(Skill::getId).sorted(Comparator.naturalOrder()).toList());
		}
	}

}
