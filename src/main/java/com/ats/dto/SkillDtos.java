package com.ats.dto;

import com.ats.domain.Skill;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class SkillDtos {

	private SkillDtos() {
	}

	public record SkillRequest(@NotBlank String name, String explanation, List<String> keywords) {
	}

	public record SkillResponse(Long id, String name, String explanation, List<String> keywords) {

		public static SkillResponse from(Skill s) {
			return new SkillResponse(s.getId(), s.getName(), s.getExplanation(), List.copyOf(s.getKeywords()));
		}
	}

}
