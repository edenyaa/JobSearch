package com.ats.controllers;

import com.ats.domain.Job;
import com.ats.dto.JobDtos.JobRequest;
import com.ats.dto.JobDtos.JobResponse;
import com.ats.service.JobService;
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

	public JobController(JobService jobs) {
		this.jobs = jobs;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public JobResponse create(@PathVariable Long userId, @Valid @RequestBody JobRequest req) {
		return JobResponse.from(jobs.create(userId, req));
	}

	@GetMapping
	public List<JobResponse> list(@PathVariable Long userId) {
		return jobs.list(userId).stream().map(JobResponse::from).toList();
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
