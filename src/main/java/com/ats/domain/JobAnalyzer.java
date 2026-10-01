package com.ats.domain;

import com.ats.domain.Analysis.Check;
import com.ats.domain.Analysis.CategoryCount;
import com.ats.domain.Analysis.CategoryScore;
import com.ats.domain.Analysis.CheckResult;
import com.ats.domain.Analysis.Component;
import com.ats.domain.Analysis.Importance;
import com.ats.domain.Analysis.KeywordGap;
import com.ats.domain.Analysis.Status;
import com.ats.domain.Analysis.Term;
import com.ats.domain.SkillCatalog.Detected;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class JobAnalyzer {

	private static final int SKILLS_WEIGHT = 50;
	private static final int KEYWORDS_WEIGHT = 20;
	private static final int REQUIREMENTS_WEIGHT = 20;
	private static final int READINESS_WEIGHT = 10;
	private static final int REQUIRED_POINTS = 2;
	private static final int PREFERRED_POINTS = 1;

	private static final Pattern PREFERRED_MARKER = Pattern.compile(
			"nice[ -]to[ -]have|a plus|is a bonus|\\bbonus\\b|\\bpreferred\\b|\\badvantage\\b|\\bdesirable\\b|good to have|not required|\\boptional\\b",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern REQUIRED_HEADING = Pattern.compile(
			"requirements?|required|must[ -]have|responsibilities|qualifications|what you|about you|you have|you will", Pattern.CASE_INSENSITIVE);

	private record Requirement(String name, SkillCategory category, boolean fromCatalog, List<String> forms) {
	}

	private final KeywordExtractor keywordExtractor = new KeywordExtractor();
	private final RequirementChecker requirementChecker;
	private final CvReadinessChecker readinessChecker = new CvReadinessChecker();

	public JobAnalyzer() {
		this(Year.now().getValue());
	}

	public JobAnalyzer(int currentYear) {
		this.requirementChecker = new RequirementChecker(currentYear);
	}

	public Analysis analyze(String jobText, String cvText, Collection<Skill> userSkills) {
		return analyze(null, jobText, cvText, userSkills);
	}

	public Analysis analyze(String jobTitle, String jobText, String cvText, Collection<Skill> userSkills) {
		String job = jobText == null ? "" : jobText;
		String cv = cvText == null ? "" : cvText;

		Map<String, Detected> cvHits = new LinkedHashMap<>();
		for (Detected detected : SkillCatalog.detect(cv)) {
			cvHits.put(key(detected.name()), detected);
		}

		Map<String, Requirement> required = new LinkedHashMap<>();
		for (Detected detected : SkillCatalog.detect(job)) {
			required.put(key(detected.name()), new Requirement(detected.name(), detected.category(), true, detected.foundForms()));
		}

		Set<String> profile = new HashSet<>();
		for (Skill skill : userSkills) {
			List<String> terms = new ArrayList<>();
			terms.add(skill.getName());
			terms.addAll(skill.getKeywords());
			for (String term : terms) {
				if (term == null || term.isBlank()) {
					continue;
				}
				Optional<SkillCatalog.Entry> known = SkillCatalog.find(term);
				String key = known.map(entry -> key(entry.name())).orElse(key(term));
				profile.add(key);
				if (known.isEmpty() && WordMatch.contains(job, term.trim()) && !required.containsKey(key)) {
					required.put(key, new Requirement(term.trim(), SkillCategory.OTHER, false, List.of(term.trim())));
				}
			}
		}

		List<String> jobLines = List.of(job.split("\\R"));
		List<Boolean> preferredLines = preferredLines(jobLines);

		List<Term> terms = new ArrayList<>();
		for (Map.Entry<String, Requirement> entry : required.entrySet()) {
			Requirement requirement = entry.getValue();
			boolean inCv = requirement.fromCatalog() ? cvHits.containsKey(entry.getKey()) : WordMatch.contains(cv, requirement.name());
			Status status = inCv ? Status.MATCHED : profile.contains(entry.getKey()) ? Status.IN_PROFILE : Status.MISSING;
			Importance importance = importanceOf(requirement.forms(), jobLines, preferredLines);
			terms.add(new Term(requirement.name(), requirement.category(), status, importance));
		}

		int matched = count(terms, Status.MATCHED);
		int inProfile = count(terms, Status.IN_PROFILE);
		int missing = count(terms, Status.MISSING);
		int coverage = weightedPercent(terms, Status.MATCHED, null);
		int potential = weightedPercent(terms, Status.MATCHED, Status.IN_PROFILE);

		Set<String> excluded = new HashSet<>();
		required.keySet().forEach(excluded::add);
		List<KeywordGap> keywords = keywordExtractor.gaps(job, cv, excluded);
		List<Check> requirementChecks = requirementChecker.check(jobTitle, job, cv);
		List<Check> readinessChecks = readinessChecker.check(cv);

		List<Component> components = components(terms, coverage, keywords, requirementChecks, readinessChecks);
		return new Analysis(
				overall(components),
				coverage,
				potential,
				terms.size(),
				matched,
				inProfile,
				missing,
				List.copyOf(terms),
				categoryScores(terms),
				cvCategories(cvHits.values()),
				extraCvSkills(cvHits, required),
				keywords,
				requirementChecks,
				readinessChecks,
				components);
	}

	private List<Component> components(List<Term> terms, int coverage, List<KeywordGap> keywords,
			List<Check> requirementChecks, List<Check> readinessChecks) {
		List<Component> components = new ArrayList<>();
		if (!terms.isEmpty()) {
			long requiredCount = terms.stream().filter(t -> t.importance() == Importance.REQUIRED).count();
			components.add(new Component("skills", "Skills", coverage, SKILLS_WEIGHT,
					requiredCount + " required and " + (terms.size() - requiredCount) + " nice-to-have skills found in the posting."));
		}
		if (!keywords.isEmpty()) {
			long inCv = keywords.stream().filter(KeywordGap::inCv).count();
			components.add(new Component("keywords", "Other keywords", percent((int) inCv, keywords.size()), KEYWORDS_WEIGHT,
					inCv + " of the " + keywords.size() + " words this posting repeats appear in your CV."));
		}
		long applicable = requirementChecks.stream().filter(c -> c.result() != CheckResult.UNKNOWN).count();
		if (applicable > 0) {
			long passed = requirementChecks.stream().filter(c -> c.result() == CheckResult.PASS).count();
			components.add(new Component("requirements", "Requirements", percent((int) passed, (int) applicable), REQUIREMENTS_WEIGHT,
					passed + " of " + applicable + " checks passed (job title, years of experience, education)."));
		}
		long readyPassed = readinessChecks.stream().filter(c -> c.result() == CheckResult.PASS).count();
		components.add(new Component("readiness", "CV readiness", percent((int) readyPassed, readinessChecks.size()), READINESS_WEIGHT,
				readyPassed + " of " + readinessChecks.size() + " checks that help an ATS read your CV."));
		return List.copyOf(components);
	}

	private int overall(List<Component> components) {
		int weights = components.stream().mapToInt(Component::weight).sum();
		if (weights == 0) {
			return 0;
		}
		double sum = components.stream().mapToDouble(c -> (double) c.percent() * c.weight()).sum();
		return (int) Math.round(sum / weights);
	}

	private List<Boolean> preferredLines(List<String> lines) {
		List<Boolean> result = new ArrayList<>();
		boolean preferredSection = false;
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty()) {
				preferredSection = false;
				result.add(false);
				continue;
			}
			boolean marker = PREFERRED_MARKER.matcher(line).find();
			boolean heading = line.endsWith(":") || (line.length() <= 25 && !line.contains(":"));
			if (heading && marker) {
				preferredSection = true;
			} else if (heading && REQUIRED_HEADING.matcher(line).find()) {
				preferredSection = false;
			}
			result.add(marker || preferredSection);
		}
		return result;
	}

	private Importance importanceOf(List<String> forms, List<String> lines, List<Boolean> preferredLines) {
		boolean seen = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			boolean present = forms.stream().anyMatch(form -> WordMatch.contains(line, form));
			if (present) {
				seen = true;
				if (!preferredLines.get(i)) {
					return Importance.REQUIRED;
				}
			}
		}
		return seen ? Importance.PREFERRED : Importance.REQUIRED;
	}

	private int weightedPercent(List<Term> terms, Status first, Status second) {
		int total = 0;
		int earned = 0;
		for (Term term : terms) {
			int points = term.importance() == Importance.REQUIRED ? REQUIRED_POINTS : PREFERRED_POINTS;
			total += points;
			if (term.status() == first || term.status() == second) {
				earned += points;
			}
		}
		return percent(earned, total);
	}

	private List<CategoryScore> categoryScores(List<Term> terms) {
		Map<SkillCategory, List<Term>> byCategory = new EnumMap<>(SkillCategory.class);
		for (Term term : terms) {
			byCategory.computeIfAbsent(term.category(), c -> new ArrayList<>()).add(term);
		}
		List<CategoryScore> scores = new ArrayList<>();
		for (Map.Entry<SkillCategory, List<Term>> entry : byCategory.entrySet()) {
			List<Term> group = entry.getValue();
			int matched = count(group, Status.MATCHED);
			scores.add(new CategoryScore(entry.getKey(), group.size(), matched, count(group, Status.IN_PROFILE),
					count(group, Status.MISSING), percent(matched, group.size())));
		}
		return List.copyOf(scores);
	}

	private List<CategoryCount> cvCategories(Collection<Detected> cvHits) {
		Map<SkillCategory, Integer> counts = new EnumMap<>(SkillCategory.class);
		for (Detected detected : cvHits) {
			counts.merge(detected.category(), 1, Integer::sum);
		}
		List<CategoryCount> result = new ArrayList<>();
		counts.forEach((category, count) -> result.add(new CategoryCount(category, count)));
		return List.copyOf(result);
	}

	private List<String> extraCvSkills(Map<String, Detected> cvHits, Map<String, Requirement> required) {
		List<String> extra = new ArrayList<>();
		for (Map.Entry<String, Detected> entry : cvHits.entrySet()) {
			if (!required.containsKey(entry.getKey())) {
				extra.add(entry.getValue().name());
			}
		}
		return List.copyOf(extra);
	}

	private int count(List<Term> terms, Status status) {
		return (int) terms.stream().filter(term -> term.status() == status).count();
	}

	private int percent(int part, int whole) {
		return whole == 0 ? 0 : Math.round(part * 100f / whole);
	}

	private String key(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

}
