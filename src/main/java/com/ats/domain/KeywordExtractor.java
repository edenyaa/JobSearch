package com.ats.domain;

import com.ats.domain.Analysis.KeywordGap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeywordExtractor {

	private static final int MIN_MENTIONS = 2;
	private static final int MAX_BIGRAMS = 6;
	private static final int MAX_TOTAL = 12;

	private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9+#]*(?:[.\\-][A-Za-z0-9+#]+)*");
	private static final Pattern SEGMENT_BREAK = Pattern.compile("[\\n,();•|]+|(?<=[.!?])\\s+");

	private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList((
			"the and for with that this from have has had are was were been being will would should could can may might must "
					+ "our your their they them you we us its it's his her him she who whom whose which what when where why how "
					+ "not but nor yet also than then thus such into onto over under about above below between among through "
					+ "during before after while within without across per via any all each every both either neither some more "
					+ "most other another only own same very just too here there these those one two three four five new "
					+ "etc using use used uses able ability abilities strong stronger strongly good great best well excellent "
					+ "experience experiences experienced years year work works working worked team teams skills skill knowledge "
					+ "role roles position positions company companies candidate candidates looking join joining required "
					+ "requirement requirements preferred plus including includes include responsibilities responsibility "
					+ "qualifications qualification opportunity opportunities benefits benefit salary location locations remote "
					+ "hybrid apply applying employer equal employees employee environment help helping make making ensure "
					+ "ensuring need needs needed want wants like looking seeking seek role day days time based high higher "
					+ "level levels related relevant various multiple including etc part full ideal successful proven "
					+ "understanding familiarity familiar demonstrated demonstrate demonstrates passion passionate").split("\\s+")));

	public List<KeywordGap> gaps(String jobText, String cvText, Set<String> excludedTerms) {
		String job = jobText == null ? "" : jobText;
		String cv = cvText == null ? "" : cvText;
		List<KeywordGap> gaps = new ArrayList<>();
		for (Map.Entry<String, Integer> candidate : extract(job, excludedTerms)) {
			gaps.add(new KeywordGap(candidate.getKey(), presentInCv(cv, candidate.getKey()), candidate.getValue()));
		}
		return List.copyOf(gaps);
	}

	List<Map.Entry<String, Integer>> extract(String jobText, Set<String> excludedTerms) {
		Map<String, Integer> unigrams = new LinkedHashMap<>();
		Map<String, Integer> bigrams = new LinkedHashMap<>();

		for (String segment : SEGMENT_BREAK.split(jobText)) {
			List<String> tokens = new ArrayList<>();
			Matcher matcher = TOKEN.matcher(segment);
			while (matcher.find()) {
				tokens.add(clean(matcher.group()));
			}
			String previous = null;
			for (String token : tokens) {
				if (!usable(token, excludedTerms)) {
					previous = null;
					continue;
				}
				unigrams.merge(token, 1, Integer::sum);
				if (previous != null) {
					String bigram = previous + " " + token;
					if (!excluded(bigram, excludedTerms)) {
						bigrams.merge(bigram, 1, Integer::sum);
					}
				}
				previous = token;
			}
		}

		Comparator<Map.Entry<String, Integer>> byFrequency = Map.Entry.<String, Integer>comparingByValue().reversed()
				.thenComparing(entry -> -entry.getKey().length())
				.thenComparing(Map.Entry::getKey);

		List<Map.Entry<String, Integer>> chosen = new ArrayList<>();
		Set<String> coveredWords = new HashSet<>();
		bigrams.entrySet().stream()
				.filter(entry -> entry.getValue() >= MIN_MENTIONS)
				.sorted(byFrequency)
				.limit(MAX_BIGRAMS)
				.forEach(entry -> {
					chosen.add(entry);
					coveredWords.addAll(Arrays.asList(entry.getKey().split(" ")));
				});
		unigrams.entrySet().stream()
				.filter(entry -> entry.getValue() >= MIN_MENTIONS && !coveredWords.contains(entry.getKey()))
				.sorted(byFrequency)
				.limit(MAX_TOTAL - chosen.size())
				.forEach(chosen::add);
		return chosen;
	}

	private boolean usable(String token, Set<String> excludedTerms) {
		return token.length() >= 3
				&& !STOPWORDS.contains(token)
				&& !token.chars().allMatch(Character::isDigit)
				&& !excluded(token, excludedTerms);
	}

	private boolean excluded(String term, Set<String> excludedTerms) {
		return SkillCatalog.find(term).isPresent()
				|| excludedTerms.contains(term)
				|| Arrays.stream(term.split(" ")).anyMatch(word -> SkillCatalog.find(word).isPresent() || excludedTerms.contains(word));
	}

	private String clean(String token) {
		return token.toLowerCase(Locale.ROOT).replaceAll("[.\\-]+$", "");
	}

	private boolean presentInCv(String cv, String term) {
		if (WordMatch.contains(cv, term)) {
			return true;
		}
		int space = term.lastIndexOf(' ');
		String head = space < 0 ? "" : term.substring(0, space + 1);
		String last = space < 0 ? term : term.substring(space + 1);
		String variant = last.endsWith("s") ? last.substring(0, last.length() - 1) : last + "s";
		return WordMatch.contains(cv, head + variant);
	}

}
