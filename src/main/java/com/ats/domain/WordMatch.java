package com.ats.domain;

import java.util.regex.Pattern;

final class WordMatch {

	private WordMatch() {
	}

	static boolean contains(String text, String term) {
		return pattern(term, false).matcher(text).find();
	}

	static boolean containsExactCase(String text, String term) {
		return pattern(term, true).matcher(text).find();
	}

	private static Pattern pattern(String term, boolean caseSensitive) {
		int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])", flags);
	}

}
