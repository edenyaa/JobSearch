package com.ats.service;

import com.ats.domain.Analysis;
import com.ats.domain.Job;
import com.ats.domain.JobAnalyzer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalysisService {

	private final JobService jobs;
	private final SkillService skills;
	private final UserService users;
	private final JobAnalyzer analyzer = new JobAnalyzer();

	public AnalysisService(JobService jobs, SkillService skills, UserService users) {
		this.jobs = jobs;
		this.skills = skills;
		this.users = users;
	}

	public Analysis analyze(Long userId, Long jobId) {
		Job job = jobs.get(userId, jobId);
		String cvText = users.mainCvText(userId);
		return analyzer.analyze(job.getTitle(), job.getDescription(), cvText, skills.list(userId));
	}

}
