package com.ats.dto;

import com.ats.domain.Company;
import com.ats.domain.ContactPerson;
import com.ats.domain.Job;
import com.ats.domain.JobStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

public final class CompanyDtos {

	private CompanyDtos() {
	}

	public record ContactBody(
			String name,
			String whereKnown,
			String linkedin,
			String phone,
			String description) {

		static ContactBody from(ContactPerson c) {
			return new ContactBody(c.getName(), c.getWhereKnown(), c.getLinkedin(), c.getPhone(), c.getDescription());
		}
	}

	/** {@code careersUrl} is optional; with a link, the job search reads that careers page. */
	public record CompanyRequest(@NotBlank String name, List<ContactBody> contacts, String careersUrl) {
	}

	/** A job submitted to the company, in short form. */
	public record CompanyJob(Long id, String jobNumber, String title, JobStatus status, LocalDate appliedDate) {

		static CompanyJob from(Job j) {
			return new CompanyJob(j.getId(), j.getJobNumber(), j.getTitle(), j.getStatus(), j.getAppliedDate());
		}
	}

	/** {@code searched} says whether the job search reads this company's careers page. */
	public record CompanyResponse(Long id, String name, String careersUrl, boolean searched, List<ContactBody> contacts,
			List<CompanyJob> jobs) {

		public static CompanyResponse from(Company c, List<Job> jobs, boolean searched) {
			return new CompanyResponse(c.getId(), c.getName(), c.getCareersUrl(), searched,
					c.getContacts().stream().map(ContactBody::from).toList(), jobs.stream().map(CompanyJob::from).toList());
		}
	}

}
