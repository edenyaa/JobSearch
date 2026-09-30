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
public class Skill {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private User user;

	@Column(nullable = false)
	private String name;

	@Column(length = 2000)
	private String explanation;

	/** Buzzwords / keywords to use in the resume so ATS systems match it. */
	@ElementCollection
	private List<String> keywords = new ArrayList<>();

}
