package com.ats.domain;

import java.util.List;

public record GapAnalysis(List<String> matchedSkills, List<String> missingKeywords, List<NamingMismatch> namingMismatches) {

	public record NamingMismatch(String jobTerm, String resumeTerm) {
	}

}
