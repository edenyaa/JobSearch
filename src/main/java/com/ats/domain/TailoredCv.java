package com.ats.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public record TailoredCv(
		String name,
		List<String> contact,
		String summary,
		List<String> skills,
		List<Experience> experience,
		List<Project> projects,
		List<Education> education,
		List<String> additional,
		List<Section> sections) {

	public static final List<String> SECTION_TYPES = List.of("summary", "skills", "experience", "projects", "education",
			"additional");

	public record Section(String type, String heading) {
	}

	public TailoredCv(String name, List<String> contact, String summary, List<String> skills,
			List<Experience> experience, List<Education> education, List<String> additional) {
		this(name, contact, summary, skills, experience, null, education, additional, null);
	}

	public TailoredCv(String name, List<String> contact, String summary, List<String> skills,
			List<Experience> experience, List<Project> projects, List<Education> education, List<String> additional) {
		this(name, contact, summary, skills, experience, projects, education, additional, null);
	}

	public TailoredCv {
		contact = contact == null ? List.of() : List.copyOf(contact);
		skills = skills == null ? List.of() : List.copyOf(skills);
		experience = experience == null ? List.of() : List.copyOf(experience);
		projects = projects == null ? List.of() : List.copyOf(projects);
		education = education == null ? List.of() : List.copyOf(education);
		additional = additional == null ? List.of() : List.copyOf(additional);
		sections = sections == null ? List.of()
				: sections.stream().filter(x -> x != null && x.type() != null && SECTION_TYPES.contains(x.type().trim().toLowerCase()))
						.map(x -> new Section(x.type().trim().toLowerCase(), x.heading())).toList();
	}

	public List<Section> orderedSections() {
		List<Section> ordered = new ArrayList<>();
		for (Section section : sections) {
			if (ordered.stream().noneMatch(o -> o.type().equals(section.type()))) {
				ordered.add(section);
			}
		}
		for (String type : SECTION_TYPES) {
			if (ordered.stream().noneMatch(o -> o.type().equals(type)) && hasContent(type)) {
				ordered.add(new Section(type, null));
			}
		}
		return ordered;
	}

	public boolean hasSection(String type) {
		return sections.isEmpty() || sections.stream().anyMatch(x -> x.type().equals(type));
	}

	public boolean hasContent(String type) {
		return switch (type) {
			case "summary" -> summary != null && !summary.isBlank();
			case "skills" -> !skills.isEmpty();
			case "experience" -> !experience.isEmpty();
			case "projects" -> !projects.isEmpty();
			case "education" -> !education.isEmpty();
			default -> !additional.isEmpty();
		};
	}

	public static String defaultHeading(String type) {
		return type.toUpperCase();
	}

	public record Experience(String role, String company, String period, List<String> bullets) {

		public Experience {
			bullets = bullets == null ? List.of() : List.copyOf(bullets);
		}
	}

	public record Project(String name, List<String> technologies, List<String> bullets) {

		public Project {
			technologies = technologies == null ? List.of() : List.copyOf(technologies);
			bullets = bullets == null ? List.of() : List.copyOf(bullets);
		}
	}

	public record Education(String degree, String institution, String period) {
	}

	public TailoredCv map(UnaryOperator<String> f) {
		return new TailoredCv(
				apply(f, name),
				contact.stream().map(s -> apply(f, s)).toList(),
				apply(f, summary),
				skills.stream().map(s -> apply(f, s)).toList(),
				experience.stream().map(e -> new Experience(apply(f, e.role()), apply(f, e.company()),
						apply(f, e.period()), e.bullets().stream().map(b -> apply(f, b)).toList())).toList(),
				projects.stream().map(p -> new Project(apply(f, p.name()),
						p.technologies().stream().map(t -> apply(f, t)).toList(),
						p.bullets().stream().map(b -> apply(f, b)).toList())).toList(),
				education.stream().map(e -> new Education(apply(f, e.degree()), apply(f, e.institution()),
						apply(f, e.period()))).toList(),
				additional.stream().map(s -> apply(f, s)).toList(),
				sections);
	}

	public String plainText() {
		List<String> lines = new ArrayList<>();
		add(lines, name);
		contact.forEach(c -> add(lines, c));
		add(lines, summary);
		skills.forEach(s -> add(lines, s));
		for (Experience e : experience) {
			add(lines, e.role());
			add(lines, e.company());
			add(lines, e.period());
			e.bullets().forEach(b -> add(lines, b));
		}
		for (Project p : projects) {
			add(lines, p.name());
			p.technologies().forEach(t -> add(lines, t));
			p.bullets().forEach(b -> add(lines, b));
		}
		for (Education e : education) {
			add(lines, e.degree());
			add(lines, e.institution());
			add(lines, e.period());
		}
		additional.forEach(a -> add(lines, a));
		return String.join("\n", lines);
	}

	public boolean isEmpty() {
		boolean noSummary = summary == null || summary.isBlank();
		return noSummary && skills.isEmpty() && experience.isEmpty() && projects.isEmpty() && education.isEmpty();
	}

	private static String apply(UnaryOperator<String> f, String value) {
		return value == null ? null : f.apply(value);
	}

	private static void add(List<String> lines, String value) {
		if (value != null && !value.isBlank()) {
			lines.add(value);
		}
	}

}
