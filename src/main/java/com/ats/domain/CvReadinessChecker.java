package com.ats.domain;

import com.ats.domain.Analysis.Check;
import com.ats.domain.Analysis.CheckResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CvReadinessChecker {

	private static final int MIN_WORDS = 200;
	private static final int MAX_WORDS = 1100;

	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern PHONE = Pattern.compile("\\+?\\(?\\d[\\d\\s().-]{6,}\\d");
	private static final Pattern YEAR = Pattern.compile("\\b(?:19|20)\\d{2}\\b");
	private static final Pattern EXPERIENCE_HEADING = heading("(?:work\\s+|professional\\s+)?experience|employment(?:\\s+history)?|work\\s+history");
	private static final Pattern EDUCATION_HEADING = heading("education(?:\\s+and\\s+training)?|academic\\s+background");
	private static final Pattern SKILLS_HEADING = heading("(?:technical\\s+|core\\s+|key\\s+)?skills|technologies|technical\\s+proficiencies");

	private static Pattern heading(String names) {
		return Pattern.compile("^\\s*(?:" + names + ")\\s*:?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	}

	public List<Check> check(String cvText) {
		String cv = cvText == null ? "" : cvText;
		List<Check> checks = new ArrayList<>();
		checks.add(simple("email", "Email address is present", EMAIL.matcher(cv).find(),
				"An ATS needs a contact email.", "Add your email address at the top of your CV."));
		checks.add(simple("phone", "Phone number is present", hasPhone(cv),
				"A phone number was found.", "Add a phone number at the top of your CV."));
		checks.add(simple("experience", "Has an Experience section", EXPERIENCE_HEADING.matcher(cv).find(),
				"Standard heading found.", "Use a plain heading like \"Experience\" so an ATS can find your work history."));
		checks.add(simple("education", "Has an Education section", EDUCATION_HEADING.matcher(cv).find(),
				"Standard heading found.", "Use a plain heading like \"Education\"."));
		checks.add(simple("skills", "Has a Skills section", SKILLS_HEADING.matcher(cv).find(),
				"Standard heading found.", "Add a \"Skills\" section. It is the first place an ATS looks for keywords."));
		checks.add(simple("dates", "Dates are present", countMatches(YEAR, cv) >= 2,
				"Dates found.", "Add dates (for example 2021 - 2024) to your jobs so experience can be counted."));
		checks.add(lengthCheck(cv));
		return List.copyOf(checks);
	}

	private Check simple(String id, String label, boolean pass, String passDetail, String failDetail) {
		return new Check(id, label, pass ? CheckResult.PASS : CheckResult.FAIL, pass ? passDetail : failDetail);
	}

	private Check lengthCheck(String cv) {
		int words = cv.isBlank() ? 0 : cv.trim().split("\\s+").length;
		if (words < MIN_WORDS) {
			return new Check("length", "CV length is reasonable", CheckResult.FAIL,
					"About " + words + " words. That is short; add detail about what you built and achieved.");
		}
		if (words > MAX_WORDS) {
			return new Check("length", "CV length is reasonable", CheckResult.FAIL,
					"About " + words + " words. That is long; aim for one or two pages.");
		}
		return new Check("length", "CV length is reasonable", CheckResult.PASS, "About " + words + " words.");
	}

	private boolean hasPhone(String cv) {
		Matcher matcher = PHONE.matcher(cv);
		while (matcher.find()) {
			long digits = matcher.group().chars().filter(Character::isDigit).count();
			if (digits >= 9 && digits <= 15) {
				return true;
			}
		}
		return false;
	}

	private int countMatches(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

}
