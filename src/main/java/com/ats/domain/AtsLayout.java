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
		for (TailoredCv.Section section : cv.orderedSections()) {
			if (!cv.hasContent(section.type())) {
				continue;
			}
			lines.add("");
			lines.add("## " + heading(section));
			switch (section.type()) {
				case "summary" -> lines.add(cv.summary());
				case "skills" -> lines.add(String.join(", ", cv.skills()));
				case "experience" -> {
					for (TailoredCv.Experience job : cv.experience()) {
						lines.add("");
						lines.add("### " + join(job.role(), job.company(), job.period()));
						job.bullets().stream().filter(AtsLayout::present).forEach(b -> lines.add("- " + b));
					}
				}
				case "projects" -> {
					for (TailoredCv.Project project : cv.projects()) {
						lines.add("");
						lines.add("### " + join(project.name(), String.join(", ", project.technologies())));
						project.bullets().stream().filter(AtsLayout::present).forEach(b -> lines.add("- " + b));
					}
				}
				case "education" -> {
					for (TailoredCv.Education education : cv.education()) {
						lines.add(join(education.degree(), education.institution(), education.period()));
					}
				}
				default -> cv.additional().stream().filter(AtsLayout::present).forEach(x -> lines.add("- " + x));
			}
		}
		return String.join("\n", lines).trim() + "\n";
	}

	public static String heading(TailoredCv.Section section) {
		return present(section.heading()) ? section.heading().trim() : TailoredCv.defaultHeading(section.type());
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
