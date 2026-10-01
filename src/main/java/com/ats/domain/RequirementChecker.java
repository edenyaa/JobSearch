package com.ats.domain;

import com.ats.domain.Analysis.Check;
import com.ats.domain.Analysis.CheckResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequirementChecker {

	private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?(?:years?|yrs?)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern RANGE = Pattern.compile(
			"\\b((?:19|20)\\d{2})\\s*(?:-|–|—|to)\\s*((?:19|20)\\d{2}|present|current|now|today)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern EDUCATION_LINE = Pattern.compile(
			"(?<![\\p{L}])(university|college|bsc|b\\.sc|bachelor|master|msc|m\\.sc|phd|degree|school|institute|diploma)(?![\\p{L}])",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern JOB_DEGREE = Pattern.compile(
			"(?<![\\p{L}])(bachelor|b\\.?sc|master|m\\.?sc|ph\\.?d|doctorate|degree)(?![\\p{L}])", Pattern.CASE_INSENSITIVE);
	private static final Pattern CV_DEGREE = Pattern.compile(
			"(?<![\\p{L}])(bachelor|b\\.?sc|master|m\\.?sc|ph\\.?d|doctorate|degree|university|college)(?![\\p{L}])",
			Pattern.CASE_INSENSITIVE);
	private static final Set<String> TITLE_STOPWORDS = Set.of("the", "and", "for", "with", "of", "in", "at", "to");

	private final int currentYear;

	public RequirementChecker(int currentYear) {
		this.currentYear = currentYear;
	}

	public List<Check> check(String jobTitle, String jobText, String cvText) {
		String job = jobText == null ? "" : jobText;
		String cv = cvText == null ? "" : cvText;
		List<Check> checks = new ArrayList<>();
		titleCheck(jobTitle, cv).ifPresent(checks::add);
		yearsCheck(job, cv).ifPresent(checks::add);
		educationCheck(job, cv).ifPresent(checks::add);
		return List.copyOf(checks);
	}

	private java.util.Optional<Check> titleCheck(String jobTitle, String cv) {
		if (jobTitle == null || jobTitle.isBlank()) {
			return java.util.Optional.empty();
		}
		List<String> tokens = Arrays.stream(jobTitle.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#]+"))
				.filter(token -> token.length() >= 3 && !TITLE_STOPWORDS.contains(token))
				.toList();
		if (tokens.isEmpty()) {
			return java.util.Optional.empty();
		}
		List<String> missing = tokens.stream().filter(token -> !inCv(cv, token)).toList();
		boolean pass = missing.size() * 2 <= tokens.size();
		String detail = missing.isEmpty()
				? "Your CV uses the words in the job title."
				: "Your CV does not mention: " + String.join(", ", missing) + ". ATS systems often rank by job-title match.";
		return java.util.Optional.of(new Check("title", "Job title words appear in your CV", pass ? CheckResult.PASS : CheckResult.FAIL, detail));
	}

	private java.util.Optional<Check> yearsCheck(String job, String cv) {
		int required = 0;
		Matcher matcher = YEARS.matcher(job);
		while (matcher.find()) {
			required = Math.max(required, Integer.parseInt(matcher.group(1)));
		}
		if (required == 0) {
			return java.util.Optional.empty();
		}
		OptionalDouble years = estimateCvYears(cv);
		if (years.isEmpty()) {
			return java.util.Optional.of(new Check("years", "Years of experience", CheckResult.UNKNOWN,
					"The job asks for " + required + "+ years, but we could not find dated experience in your CV."));
		}
		String shown = String.format(Locale.ROOT, "%.0f", years.getAsDouble());
		boolean pass = years.getAsDouble() >= required;
		return java.util.Optional.of(new Check("years", "Years of experience", pass ? CheckResult.PASS : CheckResult.FAIL,
				"The job asks for " + required + "+ years; your CV shows about " + shown + " years of dated experience."));
	}

	private java.util.Optional<Check> educationCheck(String job, String cv) {
		if (!JOB_DEGREE.matcher(job).find()) {
			return java.util.Optional.empty();
		}
		boolean pass = CV_DEGREE.matcher(cv).find();
		return java.util.Optional.of(new Check("education", "Education requirement", pass ? CheckResult.PASS : CheckResult.FAIL,
				pass ? "Your CV lists a degree or university." : "The job mentions a degree, but your CV does not list one."));
	}

	OptionalDouble estimateCvYears(String cv) {
		String[] lines = cv.split("\\R");
		List<int[]> intervals = new ArrayList<>();
		for (int i = 0; i < lines.length; i++) {
			Matcher matcher = RANGE.matcher(lines[i]);
			if (!matcher.find()) {
				continue;
			}
			boolean educationHere = EDUCATION_LINE.matcher(lines[i]).find();
			boolean educationAbove = i > 0 && EDUCATION_LINE.matcher(lines[i - 1]).find() && !RANGE.matcher(lines[i - 1]).find();
			if (educationHere || educationAbove) {
				continue;
			}
			matcher.reset();
			while (matcher.find()) {
				int start = Integer.parseInt(matcher.group(1));
				String endText = matcher.group(2).toLowerCase(Locale.ROOT);
				int end = endText.matches("\\d{4}") ? Integer.parseInt(endText) : currentYear;
				if (end >= start) {
					intervals.add(new int[] { start, end });
				}
			}
		}
		if (intervals.isEmpty()) {
			return OptionalDouble.empty();
		}
		intervals.sort(Comparator.comparingInt(interval -> interval[0]));
		int total = 0;
		int currentStart = intervals.get(0)[0];
		int currentEnd = intervals.get(0)[1];
		for (int[] interval : intervals.subList(1, intervals.size())) {
			if (interval[0] <= currentEnd) {
				currentEnd = Math.max(currentEnd, interval[1]);
			} else {
				total += currentEnd - currentStart;
				currentStart = interval[0];
				currentEnd = interval[1];
			}
		}
		total += currentEnd - currentStart;
		return OptionalDouble.of(total);
	}

	private boolean inCv(String cv, String token) {
		if (WordMatch.contains(cv, token)) {
			return true;
		}
		String variant = token.endsWith("s") ? token.substring(0, token.length() - 1) : token + "s";
		return WordMatch.contains(cv, variant);
	}

}
