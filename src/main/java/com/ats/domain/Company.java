package com.ats.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Company {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private User user;

	@Column(nullable = false)
	private String name;

	/** The company's careers page, when the user (or the job search) knows it. Jobs are searched there. */
	@Column(length = 2000)
	private String careersUrl;

	/** People the user knows at the company, in the order the user entered them. */
	@ElementCollection
	@OrderColumn
	private List<ContactPerson> contacts = new ArrayList<>();

}
