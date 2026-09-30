package com.ats.dto;

import com.ats.domain.Job;
import com.ats.domain.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class JobDtos {

	private JobDtos() {
	}

	public record JobRequest(
			String jobNumber,
			String title,
			String company,
			@NotBlank String description,
			String link,
			LocalDate appliedDate,
			JobStatus status,
			String notes) {
	}

	public record ChangeStatus(@NotNull JobStatus status) {
	}

	public record JobResponse(
			Long id,
			String jobNumber,
			String title,
			String company,
			String description,
			String link,
			LocalDate appliedDate,
			JobStatus status,
			String notes,
			String tailoredResumeFileName,
			boolean hasTailoredResume) {

		public static JobResponse from(Job j) {
			return new JobResponse(j.getId(), j.getJobNumber(), j.getTitle(), j.getCompany(), j.getDescription(),
					j.getLink(), j.getAppliedDate(), j.getStatus(), j.getNotes(), j.getTailoredResumeFileName(),
					j.getTailoredResume() != null);
		}
	}

}
