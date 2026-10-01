package com.ats.domain;

import java.util.List;
import java.util.stream.Stream;

public record ParsedJob(
		String title,
		List<String> hardRequirements,
		List<String> requiredTechnologies,
		List<String> preferredQualifications,
		List<String> domainKeywords) {

	public ParsedJob {
		hardRequirements = hardRequirements == null ? List.of() : List.copyOf(hardRequirements);
		requiredTechnologies = requiredTechnologies == null ? List.of() : List.copyOf(requiredTechnologies);
		preferredQualifications = preferredQualifications == null ? List.of() : List.copyOf(preferredQualifications);
		domainKeywords = domainKeywords == null ? List.of() : List.copyOf(domainKeywords);
	}

	public List<String> targetTerms() {
		return Stream.concat(requiredTechnologies.stream(), domainKeywords.stream())
				.filter(t -> t != null && !t.isBlank()).map(String::trim).distinct().toList();
	}

	public boolean isEmpty() {
		return hardRequirements.isEmpty() && requiredTechnologies.isEmpty() && preferredQualifications.isEmpty()
				&& domainKeywords.isEmpty();
	}

}
