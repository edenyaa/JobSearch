package com.ats.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** How well an opening fits the user, from 0 to 100. */
public final class FitScore {

	/** Added to the fit of a job in one of the user's priority places. */
	public static final int PRIORITY_BONUS = 10;

	private static final int ENTRY_LEVEL_BONUS = 10;
	private static final Pattern ENTRY_LEVEL = Pattern
			.compile("\\b(junior|jr|student|entry[- ]level|graduate|trainee|intern|internship)\\b");

	private FitScore() {
	}

	/** Without an AI opinion: most of the keyword match, plus a little for junior and student titles. */
	public static int fromKeywords(int keywordPercent, String title) {
		int bonus = isEntryLevel(title) ? ENTRY_LEVEL_BONUS : 0;
		return clamp(Math.round(keywordPercent * 0.8f) + bonus);
	}

	public static int withPriority(int fit, boolean priorityPlace) {
		return clamp(priorityPlace ? fit + PRIORITY_BONUS : fit);
	}

	public static boolean isEntryLevel(String title) {
		return title != null && ENTRY_LEVEL.matcher(title.toLowerCase(Locale.ROOT)).find();
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(100, value));
	}

}
