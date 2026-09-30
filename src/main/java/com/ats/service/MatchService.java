package com.ats.service;

import com.ats.domain.Job;
import com.ats.domain.KeywordMatcher;
import com.ats.domain.MatchResult;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MatchService {

	private final JobService jobs;
	private final SkillService skills;
	private final UserService users;
	private final KeywordMatcher matcher = new KeywordMatcher();

	public MatchService(JobService jobs, SkillService skills, UserService users) {
		this.jobs = jobs;
		this.skills = skills;
		this.users = users;
	}

	public MatchResult analyze(Long userId, Long jobId) {
		Job job = jobs.get(userId, jobId);
		String cvText = users.mainCvText(userId);
		List<String> terms = skills.list(userId).stream()
				.flatMap(skill -> Stream.concat(Stream.of(skill.getName()), skill.getKeywords().stream()))
				.toList();
		return matcher.match(job.getDescription(), cvText, terms);
	}

}
