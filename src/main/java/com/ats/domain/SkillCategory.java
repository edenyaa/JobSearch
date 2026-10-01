package com.ats.domain;

public enum SkillCategory {
	LANGUAGES("Languages"),
	FRONTEND("Frontend"),
	BACKEND("Backend & APIs"),
	DATABASES("Databases"),
	CLOUD_DEVOPS("Cloud & DevOps"),
	DATA_AI("Data & AI"),
	TESTING("Testing"),
	TOOLS("Tools"),
	PRACTICES("Practices & methods"),
	SOFT_SKILLS("Soft skills"),
	OTHER("Your own skills");

	private final String label;

	SkillCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
