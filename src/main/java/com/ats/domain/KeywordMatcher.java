package com.ats.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class KeywordMatcher {

	public MatchResult match(String jobDescription, String cvText, Collection<String> terms) {
		String job = jobDescription == null ? "" : jobDescription;
		String cv = cvText == null ? "" : cvText;

		List<String> matched = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (String term : distinctTerms(terms)) {
			if (!containsWord(job, term)) {
				continue;
			}
			if (containsWord(cv, term)) {
				matched.add(term);
			} else {
				missing.add(term);
			}
		}

		int relevant = matched.size() + missing.size();
		int coverage = relevant == 0 ? 0 : matched.size() * 100 / relevant;
		return new MatchResult(coverage, List.copyOf(matched), List.copyOf(missing));
	}

	private List<String> distinctTerms(Collection<String> terms) {
		Map<String, String> byLowerCase = new LinkedHashMap<>();
		if (terms != null) {
			for (String term : terms) {
				if (term != null && !term.isBlank()) {
					byLowerCase.putIfAbsent(term.trim().toLowerCase(), term.trim());
				}
			}
		}
		return new ArrayList<>(byLowerCase.values());
	}

	private boolean containsWord(String text, String term) {
		Pattern pattern = Pattern.compile(
				"(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		return pattern.matcher(text).find();
	}

}
