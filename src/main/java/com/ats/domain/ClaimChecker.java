package com.ats.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClaimChecker {

	private static final Pattern PARENTHETICAL = Pattern.compile("^(.+?)\\s*\\((.+)\\)$");

	public List<String> unsupported(Collection<String> claimed, String cvText, Collection<String> knownTerms) {
		String cv = cvText == null ? "" : cvText;
		List<String> unsupported = new ArrayList<>();
		if (claimed == null) {
			return unsupported;
		}
		for (String claim : claimed) {
			if (claim == null || claim.isBlank()) {
				continue;
			}
			String trimmed = claim.trim();
			boolean duplicate = unsupported.stream().anyMatch(u -> u.equalsIgnoreCase(trimmed));
			if (!supported(trimmed, cv, knownTerms) && !duplicate) {
				unsupported.add(trimmed);
			}
		}
		return unsupported;
	}

	private boolean supported(String claim, String cv, Collection<String> knownTerms) {
		if (supportedAsWhole(claim, cv, knownTerms)) {
			return true;
		}
		Matcher parenthetical = PARENTHETICAL.matcher(claim);
		if (parenthetical.matches()) {
			String base = parenthetical.group(1).trim();
			String inner = parenthetical.group(2).trim();
			return supportedAsWhole(base, cv, knownTerms) || supportedAsWhole(inner, cv, knownTerms);
		}
		return false;
	}

	private boolean supportedAsWhole(String claim, String cv, Collection<String> knownTerms) {
		if (claim.isEmpty()) {
			return false;
		}
		boolean known = knownTerms != null && knownTerms.stream().anyMatch(t -> t != null && t.trim().equalsIgnoreCase(claim));
		return known || WordMatch.contains(cv, claim);
	}

}
