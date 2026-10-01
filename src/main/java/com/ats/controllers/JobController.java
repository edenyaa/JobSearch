package com.ats.controllers;

import com.ats.domain.Job;
import com.ats.domain.JobStatus;
import com.ats.dto.JobDtos.ChangeStatus;
import com.ats.dto.JobDtos.JobRequest;
import com.ats.dto.JobDtos.JobResponse;
import com.ats.dto.AnalysisDtos.AnalysisResponse;
import com.ats.dto.AnalysisDtos.MatchResponse;
import com.ats.service.AnalysisService;
import com.ats.dto.AnalysisDtos.TailorResponse;
import com.ats.service.TailoringService;
import com.ats.service.JobService;
import com.ats.service.MatchService;
import com.ats.service.NotFoundException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/{userId}/jobs")
public class JobController {

	private final JobService jobs;
	private final MatchService matches;
	private final TailoringService tailoring;
	private final AnalysisService analysis;

	public JobController(JobService jobs, MatchService matches, TailoringService tailoring, AnalysisService analysis) {
		this.jobs = jobs;
		this.matches = matches;
		this.tailoring = tailoring;
		this.analysis = analysis;
	}

	@GetMapping("/{id}/analysis")
	public AnalysisResponse analysis(@PathVariable Long userId, @PathVariable Long id) {
		return AnalysisResponse.from(analysis.analyze(userId, id));
	}

	@PostMapping("/{id}/tailor")
	public TailorResponse tailor(@PathVariable Long userId, @PathVariable Long id) {
		return TailorResponse.from(tailoring.tailor(userId, id));
	}

	@GetMapping("/{id}/match")
	public MatchResponse match(@PathVariable Long userId, @PathVariable Long id) {
		return MatchResponse.from(matches.analyze(userId, id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public JobResponse create(@PathVariable Long userId, @Valid @RequestBody JobRequest req) {
		return JobResponse.from(jobs.create(userId, req));
	}

	@GetMapping
	public List<JobResponse> list(@PathVariable Long userId, @RequestParam(required = false) JobStatus status) {
		return jobs.list(userId, status).stream().map(JobResponse::from).toList();
	}

	@PutMapping("/{id}/status")
	public JobResponse changeStatus(@PathVariable Long userId, @PathVariable Long id,
			@Valid @RequestBody ChangeStatus req) {
		return JobResponse.from(jobs.changeStatus(userId, id, req.status()));
	}

	@GetMapping("/{id}")
	public JobResponse get(@PathVariable Long userId, @PathVariable Long id) {
		return JobResponse.from(jobs.get(userId, id));
	}

	@PutMapping("/{id}")
	public JobResponse update(@PathVariable Long userId, @PathVariable Long id, @Valid @RequestBody JobRequest req) {
		return JobResponse.from(jobs.update(userId, id, req));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long userId, @PathVariable Long id) {
		jobs.delete(userId, id);
	}

	@PutMapping(value = "/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public JobResponse uploadResume(@PathVariable Long userId, @PathVariable Long id,
			@RequestParam("file") MultipartFile file) throws IOException {
		return JobResponse.from(jobs.setTailoredResume(userId, id, file));
	}

	@GetMapping("/{id}/resume")
	public ResponseEntity<byte[]> downloadResume(@PathVariable Long userId, @PathVariable Long id) {
		Job j = jobs.get(userId, id);
		if (j.getTailoredResume() == null) {
			throw new NotFoundException("Tailored resume of job", id);
		}
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + j.getTailoredResumeFileName() + "\"")
				.contentType(j.getTailoredResumeContentType() != null
						? MediaType.parseMediaType(j.getTailoredResumeContentType())
						: MediaType.APPLICATION_OCTET_STREAM)
				.body(j.getTailoredResume());
	}

}
