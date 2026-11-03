package com.ats.domain;

/** One job opening found on a job board, before it is saved as a {@link Job}. */
public record Posting(String source, String id, String title, String company, String location, String link,
		String description) {

	/** The job number stored on the saved job, which also tells a later run the opening is already known. */
	public String jobNumber() {
		return source + "-" + id;
	}

}
