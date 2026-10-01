package com.ats.domain;

import java.util.List;

public record Analysis(
		int overallScore,
		int coveragePercent,
		int potentialPercent,
		int total,
		int matched,
		int inProfile,
		int missing,
		List<Term> terms,
		List<CategoryScore> categories,
		List<CategoryCount> cvCategories,
		List<String> extraCvSkills,
		List<KeywordGap> keywords,
		List<Check> requirementChecks,
		List<Check> readinessChecks,
		List<Component> components) {

	public enum Status {
		MATCHED,
		IN_PROFILE,
		MISSING
	}

	public enum Importance {
		REQUIRED,
		PREFERRED
	}

	public enum CheckResult {
		PASS,
		FAIL,
		UNKNOWN
	}

	public record Term(String name, SkillCategory category, Status status, Importance importance) {
	}

	public record CategoryScore(SkillCategory category, int total, int matched, int inProfile, int missing, int percent) {
	}

	public record CategoryCount(SkillCategory category, int count) {
	}

	public record KeywordGap(String term, boolean inCv, int mentions) {
	}

	public record Check(String id, String label, CheckResult result, String detail) {
	}

	public record Component(String key, String label, int percent, int weight, String detail) {
	}

}
