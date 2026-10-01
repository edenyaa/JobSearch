package com.ats.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BulletGuard {

	public static final List<String> BANNED_WORDS = List.of("spearheaded", "visionary", "passionate",
			"results-driven", "result-driven", "innovative", "synergy", "dynamic", "cutting-edge", "world-class",
			"best-in-class", "game-changing", "go-getter", "rockstar", "ninja", "guru", "proven track record",
			"detail-oriented", "self-starter", "thought leader", "leveraged", "robust", "seamless", "impactful",
			"transformative", "revolutionary", "strategic thinker", "hard-working", "motivated");

	private static final int MAX_LENGTH = 300;
	private static final Pattern NUMBER = Pattern
			.compile("\\d+(?:[.,]\\d+)*\\s?(?:%|[kKmMbB](?![\\p{L}\\p{N}])|[xX](?![\\p{L}\\p{N}]))?");

	public record Verdict(boolean accepted, boolean metricPreserved, String reason) {
	}

	public Verdict check(String original, String optimized, String resumeText) {
		boolean preserved = numbersOf(optimized).containsAll(numbersOf(original));
		if (optimized == null || optimized.isBlank()) {
			return new Verdict(false, preserved, "empty bullet");
		}
		if (optimized.length() > MAX_LENGTH) {
			return new Verdict(false, preserved, "bullet is too long");
		}
		String banned = bannedWordIn(optimized);
		if (banned != null) {
			return new Verdict(false, preserved, "uses the buzzword '" + banned + "'");
		}
		if (!preserved) {
			return new Verdict(false, false, "drops a number from the original");
		}
		Set<String> invented = numbersOf(optimized);
		invented.removeAll(numbersOf(original));
		if (!invented.isEmpty()) {
			return new Verdict(false, preserved, "adds numbers that are not in the original: " + invented);
		}
		Set<String> allowed = new HashSet<>();
		SkillCatalog.detect(resumeText).forEach(d -> allowed.add(d.name()));
		SkillCatalog.detect(original).forEach(d -> allowed.add(d.name()));
		for (SkillCatalog.Detected detected : SkillCatalog.detect(optimized)) {
			if (!allowed.contains(detected.name())) {
				return new Verdict(false, preserved, "adds the technology '" + detected.name() + "'");
			}
		}
		return new Verdict(true, preserved, "");
	}

	public String bannedWordIn(String text) {
		if (text == null) {
			return null;
		}
		for (String word : BANNED_WORDS) {
			if (WordMatch.contains(text, word)) {
				return word;
			}
		}
		return null;
	}

	private Set<String> numbersOf(String text) {
		Set<String> numbers = new HashSet<>();
		if (text == null) {
			return numbers;
		}
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			numbers.add(matcher.group().replaceAll("\\s", "").toLowerCase(Locale.ROOT));
		}
		return numbers;
	}

}
