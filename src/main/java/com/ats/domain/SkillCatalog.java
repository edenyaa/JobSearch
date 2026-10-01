package com.ats.domain;

import static com.ats.domain.SkillCategory.BACKEND;
import static com.ats.domain.SkillCategory.CLOUD_DEVOPS;
import static com.ats.domain.SkillCategory.DATABASES;
import static com.ats.domain.SkillCategory.DATA_AI;
import static com.ats.domain.SkillCategory.FRONTEND;
import static com.ats.domain.SkillCategory.LANGUAGES;
import static com.ats.domain.SkillCategory.PRACTICES;
import static com.ats.domain.SkillCategory.SOFT_SKILLS;
import static com.ats.domain.SkillCategory.TESTING;
import static com.ats.domain.SkillCategory.TOOLS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SkillCatalog {

	public record Entry(String name, SkillCategory category, List<String> forms, boolean caseSensitive) {
	}

	public record Detected(String name, SkillCategory category, List<String> foundForms) {
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final Map<String, Entry> BY_FORM = new HashMap<>();

	private SkillCatalog() {
	}

	public static List<Entry> entries() {
		return List.copyOf(ENTRIES);
	}

	public static Optional<Entry> find(String term) {
		if (term == null || term.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_FORM.get(term.trim().toLowerCase(Locale.ROOT)));
	}

	public static List<Detected> detect(String text) {
		List<Detected> detected = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return detected;
		}
		for (Entry entry : ENTRIES) {
			List<String> found = new ArrayList<>();
			for (String form : entry.forms()) {
				boolean present = entry.caseSensitive() ? WordMatch.containsExactCase(text, form) : WordMatch.contains(text, form);
				if (present) {
					found.add(form);
				}
			}
			if (!found.isEmpty()) {
				detected.add(new Detected(entry.name(), entry.category(), List.copyOf(found)));
			}
		}
		return detected.stream().filter(candidate -> !subsumed(candidate, detected)).toList();
	}

	private static boolean subsumed(Detected candidate, List<Detected> all) {
		for (Detected other : all) {
			if (other == candidate) {
				continue;
			}
			boolean covered = candidate.foundForms().stream().allMatch(form -> other.foundForms().stream()
					.anyMatch(longer -> longer.length() > form.length() && WordMatch.contains(longer, form)));
			if (covered) {
				return true;
			}
		}
		return false;
	}

	private static void add(SkillCategory category, String name, String... aliases) {
		register(category, name, false, aliases);
	}

	private static void addExactCase(SkillCategory category, String name, String... aliases) {
		register(category, name, true, aliases);
	}

	private static void register(SkillCategory category, String name, boolean caseSensitive, String... aliases) {
		List<String> forms = new ArrayList<>();
		forms.add(name);
		forms.addAll(List.of(aliases));
		Entry entry = new Entry(name, category, List.copyOf(forms), caseSensitive);
		ENTRIES.add(entry);
		for (String form : forms) {
			BY_FORM.putIfAbsent(form.toLowerCase(Locale.ROOT), entry);
		}
	}

	static {
		add(LANGUAGES, "Java");
		add(LANGUAGES, "Kotlin");
		add(LANGUAGES, "Scala");
		add(LANGUAGES, "Python");
		add(LANGUAGES, "JavaScript");
		add(LANGUAGES, "TypeScript");
		add(LANGUAGES, "C++");
		add(LANGUAGES, "C#");
		add(LANGUAGES, "Golang");
		add(LANGUAGES, "Rust");
		add(LANGUAGES, "Ruby");
		add(LANGUAGES, "PHP");
		add(LANGUAGES, "Swift");
		add(LANGUAGES, "Objective-C");
		add(LANGUAGES, "SQL");
		add(LANGUAGES, "Bash", "Shell scripting");
		add(LANGUAGES, "PowerShell");
		add(LANGUAGES, "Dart");
		add(LANGUAGES, "Perl");
		add(LANGUAGES, "Haskell");
		add(LANGUAGES, "Elixir");
		add(LANGUAGES, "MATLAB");

		add(FRONTEND, "React", "React.js", "ReactJS");
		add(FRONTEND, "Angular", "AngularJS");
		add(FRONTEND, "Vue.js", "Vue", "VueJS");
		add(FRONTEND, "Next.js", "NextJS");
		add(FRONTEND, "Redux");
		add(FRONTEND, "Svelte");
		add(FRONTEND, "HTML", "HTML5");
		add(FRONTEND, "CSS", "CSS3");
		add(FRONTEND, "Sass", "SCSS");
		add(FRONTEND, "Tailwind CSS", "Tailwind");
		add(FRONTEND, "Bootstrap");
		add(FRONTEND, "Webpack");
		add(FRONTEND, "Vite");
		add(FRONTEND, "jQuery");
		add(FRONTEND, "Responsive Design", "responsive web design");
		add(FRONTEND, "Accessibility", "WCAG");

		add(BACKEND, "Spring Boot", "SpringBoot");
		addExactCase(BACKEND, "Spring", "Spring Framework");
		add(BACKEND, "Node.js", "NodeJS");
		add(BACKEND, "Express.js", "ExpressJS");
		add(BACKEND, "NestJS");
		add(BACKEND, "Django");
		add(BACKEND, "Flask");
		add(BACKEND, "FastAPI");
		add(BACKEND, ".NET", "ASP.NET", "dotnet");
		add(BACKEND, "Laravel");
		add(BACKEND, "Hibernate");
		add(BACKEND, "JPA");
		add(BACKEND, "REST", "REST API", "REST APIs", "RESTful");
		add(BACKEND, "GraphQL");
		add(BACKEND, "gRPC");
		add(BACKEND, "Microservices", "microservice");
		add(BACKEND, "Kafka", "Apache Kafka");
		add(BACKEND, "RabbitMQ");
		add(BACKEND, "WebSocket", "WebSockets");
		add(BACKEND, "OAuth", "OAuth2", "JWT");

		add(DATABASES, "PostgreSQL", "Postgres");
		add(DATABASES, "MySQL");
		add(DATABASES, "MongoDB", "Mongo");
		add(DATABASES, "Redis");
		add(DATABASES, "SQLite");
		add(DATABASES, "Oracle Database", "Oracle DB");
		add(DATABASES, "SQL Server", "MSSQL");
		add(DATABASES, "Elasticsearch", "Elastic Search");
		add(DATABASES, "Cassandra");
		add(DATABASES, "DynamoDB");
		add(DATABASES, "Firebase");
		add(DATABASES, "NoSQL");

		add(CLOUD_DEVOPS, "AWS", "Amazon Web Services");
		add(CLOUD_DEVOPS, "Azure", "Microsoft Azure");
		add(CLOUD_DEVOPS, "GCP", "Google Cloud", "Google Cloud Platform");
		add(CLOUD_DEVOPS, "Docker");
		add(CLOUD_DEVOPS, "Kubernetes", "K8s");
		add(CLOUD_DEVOPS, "Terraform");
		add(CLOUD_DEVOPS, "Ansible");
		add(CLOUD_DEVOPS, "Jenkins");
		add(CLOUD_DEVOPS, "GitHub Actions");
		add(CLOUD_DEVOPS, "GitLab CI");
		add(CLOUD_DEVOPS, "CI/CD", "CI CD", "continuous integration", "continuous delivery", "continuous deployment");
		add(CLOUD_DEVOPS, "Linux");
		add(CLOUD_DEVOPS, "Nginx");
		add(CLOUD_DEVOPS, "Prometheus");
		add(CLOUD_DEVOPS, "Grafana");
		add(CLOUD_DEVOPS, "Helm");
		add(CLOUD_DEVOPS, "Serverless", "AWS Lambda");
		add(CLOUD_DEVOPS, "DevOps");

		add(DATA_AI, "Machine Learning");
		add(DATA_AI, "Deep Learning");
		add(DATA_AI, "TensorFlow");
		add(DATA_AI, "PyTorch");
		add(DATA_AI, "scikit-learn", "sklearn", "scikit learn");
		add(DATA_AI, "Pandas");
		add(DATA_AI, "NumPy");
		add(DATA_AI, "Spark", "Apache Spark", "PySpark");
		add(DATA_AI, "Hadoop");
		add(DATA_AI, "Airflow");
		add(DATA_AI, "Tableau");
		add(DATA_AI, "Power BI");
		add(DATA_AI, "NLP", "Natural Language Processing");
		add(DATA_AI, "LLM", "large language model", "large language models");
		add(DATA_AI, "Data Analysis", "data analytics");
		add(DATA_AI, "ETL");
		add(DATA_AI, "Snowflake");

		add(TESTING, "JUnit");
		add(TESTING, "Mockito");
		add(TESTING, "Jest");
		add(TESTING, "Vitest");
		add(TESTING, "Cypress");
		add(TESTING, "Selenium");
		add(TESTING, "Playwright");
		add(TESTING, "Pytest");
		add(TESTING, "TDD", "Test-Driven Development", "test driven development");
		add(TESTING, "Unit Testing", "unit tests", "unit test");
		add(TESTING, "Integration Testing", "integration tests");
		add(TESTING, "QA", "Quality Assurance");

		add(TOOLS, "Git");
		add(TOOLS, "GitHub");
		add(TOOLS, "GitLab");
		add(TOOLS, "Bitbucket");
		add(TOOLS, "Jira");
		add(TOOLS, "Confluence");
		add(TOOLS, "IntelliJ", "IntelliJ IDEA");
		add(TOOLS, "VS Code", "Visual Studio Code");
		add(TOOLS, "Figma");
		add(TOOLS, "Postman");
		add(TOOLS, "Maven");
		add(TOOLS, "Gradle");
		add(TOOLS, "npm");
		add(TOOLS, "Swagger", "OpenAPI");

		add(PRACTICES, "Agile");
		add(PRACTICES, "Scrum");
		add(PRACTICES, "Kanban");
		add(PRACTICES, "OOP", "Object-Oriented Programming", "object oriented programming");
		add(PRACTICES, "Design Patterns");
		add(PRACTICES, "Clean Code");
		add(PRACTICES, "Code Review", "code reviews");
		add(PRACTICES, "System Design");
		add(PRACTICES, "Data Structures");
		add(PRACTICES, "Algorithms");
		add(PRACTICES, "API Design", "API development");
		add(PRACTICES, "Debugging");
		add(PRACTICES, "Refactoring");
		add(PRACTICES, "Performance Optimization", "performance tuning");
		add(PRACTICES, "Security", "OWASP");
		add(PRACTICES, "Pair Programming");
		add(PRACTICES, "SaaS");

		add(SOFT_SKILLS, "Leadership");
		add(SOFT_SKILLS, "Communication");
		add(SOFT_SKILLS, "Teamwork", "team player", "collaboration");
		add(SOFT_SKILLS, "Problem Solving", "problem-solving");
		add(SOFT_SKILLS, "Mentoring", "mentorship");
		add(SOFT_SKILLS, "Project Management");
		add(SOFT_SKILLS, "Time Management");
		add(SOFT_SKILLS, "Stakeholder Management");
		add(SOFT_SKILLS, "Critical Thinking");
		add(SOFT_SKILLS, "Adaptability");
		add(SOFT_SKILLS, "Ownership");
	}

}
