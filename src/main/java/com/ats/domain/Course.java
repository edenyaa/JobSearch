package com.ats.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Course {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private User user;

	/** Course number, e.g. "CS101". */
	@Column(nullable = false)
	private String courseNumber;

	/** Where it was studied (university, bootcamp, platform...). */
	private String institution;

	@Column(length = 4000)
	private String description;

	/** Descriptions of the assignments/projects done in the course. */
	@ElementCollection
	@Column(length = 2000)
	private List<String> assignments = new ArrayList<>();

	@ManyToMany
	private Set<Skill> skills = new HashSet<>();

}
