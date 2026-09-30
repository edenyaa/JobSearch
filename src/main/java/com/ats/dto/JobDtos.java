package com.ats.dto;

import com.ats.domain.Job;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public final class JobDtos {

	private JobDtos() {
	}

	/** jobNumber is optional: when missing, a generated number is assigned. */
	public record JobRequest(
			String jobNumber,
			String title,
			String company,
			@NotBlank String description,
			String link,
			LocalDate appliedDate) {
	}

	public record JobResponse(
			Long id,
			String jobNumber,
			String title,
			String company,
			String description,
			String link,
			LocalDate appliedDate,
			String tailoredResumeFileName,
			boolean hasTailoredResume) {

		public static JobResponse from(Job j) {
			return new JobResponse(j.getId(), j.getJobNumber(), j.getTitle(), j.getCompany(), j.getDescription(),
					j.getLink(), j.getAppliedDate(), j.getTailoredResumeFileName(), j.getTailoredResume() != null);
		}
	}

}
