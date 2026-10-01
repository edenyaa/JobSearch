package com.ats.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiiRedactor {

	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern LINK = Pattern.compile(
			"(?i)(?:https?://|www\\.|linkedin\\.com/|github\\.com/)\\S*[^\\s.,;)\\]]");
	private static final Pattern PHONE = Pattern.compile("\\+?\\(?\\d[\\d\\s().-]{6,}\\d");

	public record Redaction(String text, Map<String, String> replacements) {

		public String restore(String value) {
			if (value == null) {
				return null;
			}
			String restored = value;
			for (Map.Entry<String, String> entry : replacements.entrySet()) {
				restored = restored.replace(entry.getKey(), entry.getValue());
			}
			return restored;
		}
	}

	public Redaction redact(String text, String knownName) {
		Map<String, String> replacements = new LinkedHashMap<>();
		String result = text == null ? "" : text;
		result = replaceAll(result, EMAIL, "EMAIL", replacements, 0);
		result = replaceAll(result, LINK, "LINK", replacements, 0);
		result = replaceAll(result, PHONE, "PHONE", replacements, 9);
		if (knownName != null && !knownName.isBlank()) {
			Pattern name = Pattern.compile(Pattern.quote(knownName.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			Matcher matcher = name.matcher(result);
			if (matcher.find()) {
				replacements.put("[[NAME]]", knownName.trim());
				result = name.matcher(result).replaceAll(Matcher.quoteReplacement("[[NAME]]"));
			}
		}
		return new Redaction(result, replacements);
	}

	private String replaceAll(String text, Pattern pattern, String label, Map<String, String> replacements, int minDigits) {
		Matcher matcher = pattern.matcher(text);
		StringBuilder out = new StringBuilder();
		int counter = 0;
		while (matcher.find()) {
			String found = matcher.group();
			long digits = found.chars().filter(Character::isDigit).count();
			if (minDigits > 0 && (digits < minDigits || digits > 15)) {
				matcher.appendReplacement(out, Matcher.quoteReplacement(found));
				continue;
			}
			String placeholder = "[[" + label + "_" + (++counter) + "]]";
			replacements.put(placeholder, found);
			matcher.appendReplacement(out, Matcher.quoteReplacement(placeholder));
		}
		matcher.appendTail(out);
		return out.toString();
	}

}
