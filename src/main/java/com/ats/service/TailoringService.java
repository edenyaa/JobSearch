package com.ats.service;

import com.ats.domain.AtsLayout;
import com.ats.domain.ClaimChecker;
import com.ats.domain.Course;
import com.ats.domain.Job;
import com.ats.domain.KeywordMatcher;
import com.ats.domain.MatchResult;
import com.ats.domain.PiiRedactor;
import com.ats.domain.PiiRedactor.Redaction;
import com.ats.domain.Skill;
import com.ats.domain.SkillCatalog;
import com.ats.domain.TailoredCv;
import com.ats.domain.TailoredResumePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TailoringService {

	private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

	private final JobService jobs;
	private final UserService users;
	private final SkillService skills;
	private final CourseService courses;
	private final TailoringPipeline pipeline;
	private final ResumeRenderer renderer;
	private final KeywordMatcher matcher = new KeywordMatcher();
	private final PiiRedactor redactor = new PiiRedactor();
	private final ClaimChecker claimChecker = new ClaimChecker();

	public TailoringService(JobService jobs, UserService users, SkillService skills, CourseService courses,
			TailoringPipeline pipeline, ResumeRenderer renderer) {
		this.jobs = jobs;
		this.users = users;
		this.skills = skills;
		this.courses = courses;
		this.pipeline = pipeline;
		this.renderer = renderer;
	}

	@Transactional
	public TailoringResult tailor(Long userId, Long jobId) {
		Job job = jobs.get(userId, jobId);
		String cvText = users.mainCvText(userId);
		List<Skill> userSkills = skills.list(userId);
		List<Course> userCourses = courses.list(userId);
		List<String> terms = KeywordMatcher.termsOf(userSkills);

		Redaction redaction = redactor.redact(cvText, users.get(userId).getName());
		List<String> skillNames = userSkills.stream().map(Skill::getName).toList();
		TailoredResumePayload payload = pipeline.run(job.getTitle(), job.getCompany(), job.getDescription(),
				redaction.text(), skillNames, relevantCourses(job.getDescription(), userCourses));

		TailoredCv tailored = payload.resume().map(redaction::restore);
		byte[] file = renderer.render(tailored);
		jobs.storeTailoredResume(userId, jobId, fileName(job), DOCX_TYPE, file);

		MatchResult before = matcher.match(job.getDescription(), cvText, terms);
		MatchResult after = matcher.match(job.getDescription(), tailored.plainText(), terms);
		List<String> unsupported = claimChecker.unsupported(tailored.skills(), cvText, terms);
		return new TailoringResult(job, before, after, unsupported, payload.gap(), payload.bullets(),
				payload.rejectedBullets(), AtsLayout.markdown(tailored));
	}

	private List<String> relevantCourses(String jobDescription, List<Course> userCourses) {
		Set<String> wanted = SkillCatalog.detect(jobDescription).stream().map(SkillCatalog.Detected::name)
				.collect(Collectors.toSet());
		List<String> lines = new ArrayList<>();
		for (Course course : userCourses) {
			StringBuilder text = new StringBuilder();
			append(text, course.getDescription());
			course.getAssignments().forEach(a -> append(text, a));
			course.getSkills().forEach(s -> append(text, s.getName()));
			boolean relevant = SkillCatalog.detect(text.toString()).stream().anyMatch(d -> wanted.contains(d.name()));
			if (relevant) {
				String institution = course.getInstitution() == null || course.getInstitution().isBlank() ? ""
						: " (" + course.getInstitution() + ")";
				lines.add("Course: " + course.getCourseNumber() + institution);
			}
		}
		return lines;
	}

	private void append(StringBuilder text, String value) {
		if (value != null) {
			text.append(value).append('\n');
		}
	}

	private String fileName(Job job) {
		return "tailored-cv-" + job.getJobNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".docx";
	}

}
