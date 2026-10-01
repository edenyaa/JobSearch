package com.ats.domain;

import com.ats.domain.GapAnalysis.NamingMismatch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminologyNormalizer {

	public TailoredCv normalize(TailoredCv cv, List<NamingMismatch> mismatches) {
		if (mismatches == null || mismatches.isEmpty()) {
			return cv;
		}
		TailoredCv replaced = cv.map(text -> normalize(text, mismatches));
		return new TailoredCv(replaced.name(), replaced.contact(), replaced.summary(), distinct(replaced.skills()),
				replaced.experience(), replaced.projects(), replaced.education(), replaced.additional());
	}

	public String normalize(String text, List<NamingMismatch> mismatches) {
		if (text == null || mismatches == null) {
			return text;
		}
		String result = text;
		for (NamingMismatch mismatch : mismatches) {
			Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(mismatch.resumeTerm())
					+ "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			Matcher matcher = pattern.matcher(result);
			result = matcher.replaceAll(Matcher.quoteReplacement(mismatch.jobTerm()));
		}
		return result;
	}

	private List<String> distinct(List<String> values) {
		Map<String, String> byKey = new LinkedHashMap<>();
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				byKey.putIfAbsent(value.trim().toLowerCase(Locale.ROOT), value.trim());
			}
		}
		return List.copyOf(byKey.values());
	}

}
