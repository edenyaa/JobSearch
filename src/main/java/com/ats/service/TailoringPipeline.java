package com.ats.service;

import com.ats.domain.AtsLayout;
import com.ats.domain.BulletGuard;
import com.ats.domain.GapAnalysis;
import com.ats.domain.GapAnalyzer;
import com.ats.domain.OptimizedBullet;
import com.ats.domain.ParsedJob;
import com.ats.domain.TailoredCv;
import com.ats.domain.TailoredCv.Experience;
import com.ats.domain.TailoredCv.Project;
import com.ats.domain.TailoredResumePayload;
import com.ats.domain.TerminologyNormalizer;
import com.ats.service.PipelinePrompts.BulletInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TailoringPipeline {

	record BulletItem(Integer id, String optimizedBullet, String matchedRequirement) {
	}

	record BulletsReply(List<BulletItem> bullets) {
	}

	private final LlmClient llm;
	private final JsonMapper mapper;
	private final GapAnalyzer gapAnalyzer = new GapAnalyzer();
	private final TerminologyNormalizer normalizer = new TerminologyNormalizer();
	private final BulletGuard guard = new BulletGuard();

	public TailoringPipeline(LlmClient llm, JsonMapper mapper) {
		this.llm = llm;
		this.mapper = mapper;
	}

	public TailoredResumePayload run(String jobTitle, String company, String jobDescription, String resumeText,
			Collection<String> userSkills, List<String> extraAdditional) {
		CompletableFuture<ParsedJob> jobFuture = CompletableFuture
				.supplyAsync(() -> parseJob(jobTitle, company, jobDescription));
		CompletableFuture<TailoredCv> resumeFuture = CompletableFuture.supplyAsync(() -> parseResume(resumeText));
		ParsedJob job = await(jobFuture);
		TailoredCv parsed = await(resumeFuture);
		return assemble(job, parsed, resumeText, userSkills, extraAdditional);
	}

	TailoredResumePayload assemble(ParsedJob job, TailoredCv parsed, String resumeText, Collection<String> userSkills,
			List<String> extraAdditional) {
		List<String> knownSkills = new ArrayList<>(parsed.skills());
		if (userSkills != null) {
			knownSkills.addAll(userSkills);
		}
		GapAnalysis gap = gapAnalyzer.analyze(job, resumeText, knownSkills);

		TailoredCv withSkills = withSkills(parsed, knownSkills, gap, extraAdditional);
		TailoredCv normalized = normalizer.normalize(withSkills, gap.namingMismatches());

		String groundTruth = resumeText + "\n" + String.join("\n", knownSkills);
		List<OptimizedBullet> accepted = new ArrayList<>();
		List<String> rejected = new ArrayList<>();
		TailoredCv optimized = optimizeBullets(job, normalized, groundTruth, gap, accepted, rejected);
		return new TailoredResumePayload(optimized, List.copyOf(accepted), gap, List.copyOf(rejected));
	}

	ParsedJob parseJob(String title, String company, String description) {
		String raw = llm.generateJson(PipelinePrompts.PARSE_JOB_SYSTEM,
				PipelinePrompts.parseJobUser(title, company, description), PipelinePrompts.TEMPERATURE,
				PipelinePrompts.jobSchema());
		ParsedJob job = read(raw, ParsedJob.class, "job posting");
		if (job.isEmpty()) {
			throw new LlmException("The AI could not find any requirements in the job posting. Try again.");
		}
		return job;
	}

	TailoredCv parseResume(String resumeText) {
		String raw = llm.generateJson(PipelinePrompts.PARSE_RESUME_SYSTEM, PipelinePrompts.parseResumeUser(resumeText),
				PipelinePrompts.TEMPERATURE, PipelinePrompts.resumeSchema());
		TailoredCv cv = read(raw, TailoredCv.class, "resume");
		if (cv.isEmpty()) {
			throw new LlmException("The AI returned an empty CV. Try again.");
		}
		return cv;
	}

	private TailoredCv withSkills(TailoredCv cv, List<String> skills, GapAnalysis gap, List<String> extraAdditional) {
		Set<String> matched = new HashSet<>();
		gap.matchedSkills().forEach(m -> matched.add(m.toLowerCase(Locale.ROOT)));
		Map<String, String> unique = new LinkedHashMap<>();
		for (String skill : skills) {
			if (skill != null && !skill.isBlank()) {
				unique.putIfAbsent(skill.trim().toLowerCase(Locale.ROOT), skill.trim());
			}
		}
		List<String> ordered = new ArrayList<>();
		unique.forEach((key, value) -> {
			if (matched.contains(key)) {
				ordered.add(value);
			}
		});
		unique.forEach((key, value) -> {
			if (!matched.contains(key)) {
				ordered.add(value);
			}
		});
		List<String> additional = new ArrayList<>(cv.additional());
		if (extraAdditional != null) {
			extraAdditional.stream().filter(a -> !additional.contains(a)).forEach(additional::add);
		}
		return new TailoredCv(cv.name(), cv.contact(), cv.summary(), ordered, cv.experience(), cv.projects(),
				cv.education(), additional);
	}

	private TailoredCv optimizeBullets(ParsedJob job, TailoredCv cv, String groundTruth, GapAnalysis gap,
			List<OptimizedBullet> accepted, List<String> rejected) {
		List<BulletInput> inputs = new ArrayList<>();
		for (Experience e : cv.experience()) {
			e.bullets().forEach(b -> inputs.add(new BulletInput(inputs.size(), context(e.role(), e.company()), b)));
		}
		for (Project p : cv.projects()) {
			p.bullets().forEach(b -> inputs.add(new BulletInput(inputs.size(), context(p.name(), "project"), b)));
		}
		if (inputs.isEmpty()) {
			return cv;
		}

		String raw = llm.generateJson(PipelinePrompts.BULLET_SYSTEM, PipelinePrompts.bulletsUser(job, inputs),
				PipelinePrompts.TEMPERATURE, PipelinePrompts.bulletsSchema());
		Map<Integer, BulletItem> byId = new HashMap<>();
		for (BulletItem item : read(raw, BulletsReply.class, "bullets").bullets()) {
			if (item != null && item.id() != null) {
				byId.put(item.id(), item);
			}
		}

		Map<Integer, String> finalText = new HashMap<>();
		for (BulletInput input : inputs) {
			BulletItem item = byId.get(input.id());
			if (item == null) {
				finalText.put(input.id(), input.text());
				rejected.add(input.text() + " (kept: no result from the AI)");
				continue;
			}
			String candidate = normalizer.normalize(item.optimizedBullet() == null ? null : item.optimizedBullet().trim(),
					gap.namingMismatches());
			BulletGuard.Verdict verdict = guard.check(input.text(), candidate, groundTruth);
			if (verdict.accepted()) {
				finalText.put(input.id(), candidate);
				accepted.add(new OptimizedBullet(input.text(), candidate,
						item.matchedRequirement() == null ? "" : item.matchedRequirement(), verdict.metricPreserved()));
			} else {
				finalText.put(input.id(), input.text());
				rejected.add(input.text() + " (kept original: " + verdict.reason() + ")");
			}
		}

		int[] next = {0};
		List<Experience> experience = cv.experience().stream()
				.map(e -> new Experience(e.role(), e.company(), e.period(),
						e.bullets().stream().map(b -> finalText.get(next[0]++)).toList()))
				.toList();
		List<Project> projects = cv.projects().stream()
				.map(p -> new Project(p.name(), p.technologies(),
						p.bullets().stream().map(b -> finalText.get(next[0]++)).toList()))
				.toList();
		return new TailoredCv(cv.name(), cv.contact(), cv.summary(), cv.skills(), experience, projects, cv.education(),
				cv.additional());
	}

	private String context(String first, String second) {
		return AtsLayout.join(first, second);
	}

	private <T> T await(CompletableFuture<T> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			if (e.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw e;
		}
	}

	private <T> T read(String raw, Class<T> type, String what) {
		String json = raw == null ? "" : raw.trim();
		if (json.startsWith("```")) {
			int firstNewline = json.indexOf('\n');
			json = firstNewline < 0 ? "" : json.substring(firstNewline + 1);
			if (json.endsWith("```")) {
				json = json.substring(0, json.length() - 3);
			}
		}
		try {
			return mapper.readValue(json.trim(), type);
		} catch (JacksonException e) {
			throw new LlmException("The AI returned a " + what + " that could not be read. Try again.");
		}
	}

}
