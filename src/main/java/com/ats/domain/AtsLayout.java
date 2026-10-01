package com.ats.domain;

import java.util.ArrayList;
import java.util.List;

public final class AtsLayout {

	private AtsLayout() {
	}

	public static String markdown(TailoredCv cv) {
		List<String> lines = new ArrayList<>();
		if (present(cv.name())) {
			lines.add("# " + cv.name());
		}
		cv.contact().stream().filter(AtsLayout::present).forEach(lines::add);
		if (present(cv.summary())) {
			lines.add("");
			lines.add("## SUMMARY");
			lines.add(cv.summary());
		}
		if (!cv.skills().isEmpty()) {
			lines.add("");
			lines.add("## SKILLS");
			lines.add(String.join(", ", cv.skills()));
		}
		if (!cv.experience().isEmpty()) {
			lines.add("");
			lines.add("## EXPERIENCE");
			for (TailoredCv.Experience job : cv.experience()) {
				lines.add("");
				lines.add("### " + join(job.role(), job.company(), job.period()));
				job.bullets().stream().filter(AtsLayout::present).forEach(b -> lines.add("- " + b));
			}
		}
		if (!cv.projects().isEmpty()) {
			lines.add("");
			lines.add("## PROJECTS");
			for (TailoredCv.Project project : cv.projects()) {
				lines.add("");
				lines.add("### " + join(project.name(), String.join(", ", project.technologies())));
				project.bullets().stream().filter(AtsLayout::present).forEach(b -> lines.add("- " + b));
			}
		}
		if (!cv.education().isEmpty()) {
			lines.add("");
			lines.add("## EDUCATION");
			for (TailoredCv.Education education : cv.education()) {
				lines.add(join(education.degree(), education.institution(), education.period()));
			}
		}
		if (!cv.additional().isEmpty()) {
			lines.add("");
			lines.add("## ADDITIONAL");
			cv.additional().stream().filter(AtsLayout::present).forEach(a -> lines.add("- " + a));
		}
		return String.join("\n", lines).trim() + "\n";
	}

	public static String join(String... parts) {
		StringBuilder joined = new StringBuilder();
		for (String part : parts) {
			if (present(part)) {
				if (joined.length() > 0) {
					joined.append(" | ");
				}
				joined.append(part);
			}
		}
		return joined.toString();
	}

	private static boolean present(String value) {
		return value != null && !value.isBlank();
	}

}
