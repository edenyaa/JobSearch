package com.ats.domain;

import com.ats.domain.GapAnalysis.NamingMismatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class GapAnalyzer {

	public GapAnalysis analyze(ParsedJob job, String resumeText, Collection<String> knownSkills) {
		String text = resumeText == null ? "" : resumeText;
		List<String> matched = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		List<NamingMismatch> mismatches = new ArrayList<>();

		for (String term : job.targetTerms()) {
			boolean inText = WordMatch.contains(text, term);
			Optional<String> textVariant = inText ? Optional.empty() : variantIn(text, term);
			if (textVariant.isPresent()) {
				mismatches.add(new NamingMismatch(term, textVariant.get()));
				if (known(knownSkills, term)) {
					matched.add(term);
				}
			} else if (inText || known(knownSkills, term)) {
				matched.add(term);
			} else {
				Optional<String> skillVariant = variantInSkills(knownSkills, term);
				if (skillVariant.isPresent()) {
					mismatches.add(new NamingMismatch(term, skillVariant.get()));
				} else {
					missing.add(term);
				}
			}
		}
		return new GapAnalysis(List.copyOf(matched), List.copyOf(missing), List.copyOf(mismatches));
	}

	private boolean known(Collection<String> knownSkills, String term) {
		return knownSkills != null && knownSkills.stream().anyMatch(s -> s != null && s.trim().equalsIgnoreCase(term.trim()));
	}

	private Optional<String> variantIn(String text, String term) {
		return forms(term).stream().filter(form -> WordMatch.contains(text, form)).findFirst();
	}

	private Optional<String> variantInSkills(Collection<String> knownSkills, String term) {
		return forms(term).stream().filter(form -> known(knownSkills, form)).findFirst();
	}

	private List<String> forms(String term) {
		Optional<SkillCatalog.Entry> entry = SkillCatalog.find(term);
		if (entry.isEmpty() || entry.get().caseSensitive()) {
			return List.of();
		}
		return entry.get().forms().stream().filter(form -> !form.equalsIgnoreCase(term.trim())).toList();
	}

}
