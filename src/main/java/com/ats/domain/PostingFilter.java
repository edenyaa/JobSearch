package com.ats.domain;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decides which openings are worth saving, and turns the boards' HTML descriptions into plain text. */
public final class PostingFilter {

	private static final Pattern BLOCK_TAG = Pattern.compile("(?i)</?(br|p|div|li|ul|ol|h[1-6])\\b[^>]*>");
	private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");
	private static final Pattern ENTITY = Pattern.compile("&(#\\d+|amp|lt|gt|quot|apos|nbsp);");

	private PostingFilter() {
	}

	/**
	 * The title must contain one of {@code titles}, none of {@code excluded} as a whole word, and the place must
	 * contain one of {@code locations} (an empty list accepts every place).
	 */
	public static boolean wanted(Posting p, List<String> titles, List<String> excluded, List<String> locations) {
		String title = p.title().toLowerCase(Locale.ROOT);
		if (titles.stream().noneMatch(t -> titleHas(title, t))) {
			return false;
		}
		for (String word : excluded) {
			if (Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE).matcher(title).find()) {
				return false;
			}
		}
		String place = p.location().toLowerCase(Locale.ROOT);
		return locations.isEmpty() || locations.stream().anyMatch(l -> place.contains(l.toLowerCase(Locale.ROOT)));
	}

	/**
	 * A wanted title term must start a word of the title ("developer" finds "Developers"), and a short one such as
	 * "ai" or "qa" must be a whole word, so "ai" does not find "email" or "maintain".
	 */
	private static boolean titleHas(String title, String term) {
		String t = term.strip().toLowerCase(Locale.ROOT);
		if (t.isEmpty()) {
			return false;
		}
		if (t.equals("intern")) {
			// "intern", "interns", "internship", but not "internal" or "international"
			return Pattern.compile("\\bintern(?:s|ship|ships)?\\b").matcher(title).find();
		}
		String end = t.length() <= 3 ? "\\b" : "";
		return Pattern.compile("\\b" + Pattern.quote(t) + end).matcher(title).find();
	}

	/** True when the opening is in one of the places the user ranks first. */
	public static boolean inPriorityPlace(Posting p, List<String> priorityLocations) {
		String place = p.location().toLowerCase(Locale.ROOT);
		return priorityLocations.stream().anyMatch(l -> !l.isBlank() && place.contains(l.toLowerCase(Locale.ROOT)));
	}

	/** HTML (Greenhouse sends it entity-escaped) to readable plain text. */
	public static String text(String markup) {
		if (markup == null) {
			return "";
		}
		String html = unescape(markup);
		html = BLOCK_TAG.matcher(html).replaceAll("\n");
		html = ANY_TAG.matcher(html).replaceAll("");
		return unescape(html).replaceAll("[ \\t\\r\\f]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").strip();
	}

	private static String unescape(String s) {
		Matcher m = ENTITY.matcher(s);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String e = m.group(1);
			String replacement = switch (e) {
				case "amp" -> "&";
				case "lt" -> "<";
				case "gt" -> ">";
				case "quot" -> "\"";
				case "apos" -> "'";
				case "nbsp" -> " ";
				default -> new String(Character.toChars(Integer.parseInt(e.substring(1))));
			};
			m.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(out);
		return out.toString();
	}

	/** Lower-case letters and digits only, so "Wix.com Ltd" and "wix com ltd" compare equal. */
	public static String key(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** Likely career-board names for a company: "Acme Corp Ltd" gives "acmecorp" and "acme-corp". */
	public static List<String> slugGuesses(String company) {
		String base = company.toLowerCase(Locale.ROOT)
				.replaceAll("\\b(inc|ltd|llc|gmbh|corp|co|limited)\\b\\.?", "")
				.replaceAll("[^a-z0-9 ]", "").strip();
		return List.of(base.replace(" ", ""), base.replace(" ", "-")).stream().filter(s -> !s.isEmpty()).distinct()
				.toList();
	}

}
