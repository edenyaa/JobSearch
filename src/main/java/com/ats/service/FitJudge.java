package com.ats.service;

import com.ats.domain.PiiRedactor;
import com.ats.domain.Posting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Asks the AI how well a job fits the user's CV. It answers nothing when the AI is unavailable. */
@Component
public class FitJudge {

	public record Fit(int score, String reason) {
	}

	private static final Logger log = LoggerFactory.getLogger(FitJudge.class);

	private static final int MAX_CV = 6000;
	private static final int MAX_JOB = 5000;

	static final String SYSTEM = """
			You are a strict recruiter helping a candidate in Israel who wants junior, student or entry-level roles. \
			Rate how well ONE job opening fits the candidate's CV, from 0 to 100.
			Consider: the skills and tools the CV really shows against what the job requires; seniority (the candidate \
			is junior, so heavily penalize jobs asking for 3 or more years of experience); and the type of role \
			(the candidate wants the kinds of roles listed, such as QA, software development, AI or LLM backend, \
			prompt engineering).
			Give 80 or more only if the candidate could realistically get an interview. Give under 40 if the role is \
			a different field or far above the candidate's level.
			Answer with JSON: score (integer 0-100) and reason (one short sentence saying what fits or what is missing).""";

	private final LlmClient llm;
	private final JsonMapper mapper;
	private final PiiRedactor redactor = new PiiRedactor();

	public FitJudge(LlmClient llm, JsonMapper mapper) {
		this.llm = llm;
		this.mapper = mapper;
	}

	public Optional<Fit> judge(String cvText, Posting posting, String description, List<String> wantedRoles) {
		try {
			String cv = redactor.redact(cvText, null).text();
			String prompt = "Roles the candidate wants: " + String.join(", ", wantedRoles) + "\n\nCV:\n"
					+ cut(cv, MAX_CV) + "\n\nJOB OPENING\nTitle: " + posting.title() + "\nCompany: " + posting.company()
					+ "\nPlace: " + posting.location() + "\n\n" + cut(description, MAX_JOB);
			String raw = llm.generateJson(SYSTEM, prompt, 0.2, schema());
			JsonNode answer = mapper.readTree(raw);
			if (!answer.path("score").isNumber()) {
				return Optional.empty();
			}
			int score = Math.max(0, Math.min(100, answer.path("score").asInt()));
			return Optional.of(new Fit(score, answer.path("reason").asString("")));
		} catch (RuntimeException e) {
			log.info("The AI could not rate a job: {}", e.getMessage());
			return Optional.empty();
		}
	}

	private static String cut(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}

	private static Map<String, Object> schema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("score", Map.of("type", "integer"));
		properties.put("reason", Map.of("type", "string"));
		return Map.of("type", "object", "properties", properties, "required", List.of("score", "reason"));
	}

}
