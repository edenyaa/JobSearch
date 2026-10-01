package com.ats.service;

import com.ats.domain.Job;
import com.ats.domain.JobStatus;
import com.ats.dto.JobDtos.JobRequest;
import com.ats.repository.JobRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class JobService {

	private final JobRepository jobs;
	private final UserService users;

	public JobService(JobRepository jobs, UserService users) {
		this.jobs = jobs;
		this.users = users;
	}

	public Job create(Long userId, JobRequest req) {
		Job j = new Job();
		j.setUser(users.get(userId));
		apply(j, req);
		boolean generate = j.getJobNumber() == null;
		if (generate) {
			j.setJobNumber("pending"); // replaced below once the id exists
		}
		jobs.saveAndFlush(j);
		if (generate) {
			j.setJobNumber("GEN-" + j.getId());
		}
		return j;
	}

	@Transactional(readOnly = true)
	public List<Job> list(Long userId, JobStatus status) {
		if (status == null) {
			return jobs.findByUserIdOrderByAppliedDateDesc(userId);
		}
		return jobs.findByUserIdAndStatusOrderByAppliedDateDesc(userId, status);
	}

	public Job changeStatus(Long userId, Long id, JobStatus status) {
		Job j = get(userId, id);
		j.setStatus(status);
		return j;
	}

	@Transactional(readOnly = true)
	public Job get(Long userId, Long id) {
		return jobs.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Job", id));
	}

	public Job update(Long userId, Long id, JobRequest req) {
		Job j = get(userId, id);
		String previous = j.getJobNumber();
		apply(j, req);
		if (j.getJobNumber() == null) {
			j.setJobNumber(previous);
		}
		return j;
	}

	public void delete(Long userId, Long id) {
		jobs.delete(get(userId, id));
	}

	public Job setTailoredResume(Long userId, Long id, MultipartFile file) throws IOException {
		return storeTailoredResume(userId, id, file.getOriginalFilename(), file.getContentType(), file.getBytes());
	}

	public Job storeTailoredResume(Long userId, Long id, String fileName, String contentType, byte[] data) {
		Job j = get(userId, id);
		j.setTailoredResumeFileName(fileName);
		j.setTailoredResumeContentType(contentType);
		j.setTailoredResume(data);
		return j;
	}

	private void apply(Job j, JobRequest req) {
		String number = req.jobNumber() == null ? "" : req.jobNumber().trim();
		j.setJobNumber(number.isEmpty() ? null : number);
		j.setTitle(req.title());
		j.setCompany(req.company());
		j.setDescription(req.description());
		j.setLink(req.link());
		if (req.status() != null) {
			j.setStatus(req.status());
		}
		if (req.notes() != null) {
			j.setNotes(req.notes());
		}
		j.setAppliedDate(req.appliedDate() != null ? req.appliedDate()
				: j.getAppliedDate() != null ? j.getAppliedDate() : LocalDate.now());
	}

}
