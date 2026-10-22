package com.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Someone the user knows at a company, and how to reach them. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ContactPerson {

		private String name;

	/** Where the user met or knows the person from (a past job, university, an event...). */
		private String whereKnown;

	@Column(length = 2000)
	private String linkedin;

		private String phone;

	/** Free text about how the user knows the person. */
	@Column(length = 4000)
	private String description;

}
