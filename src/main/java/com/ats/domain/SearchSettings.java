package com.ats.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What the user's job search looks for, and whether it also runs by itself every day. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SearchSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(unique = true)
	private User user;

	/** A job title must contain one of these. */
	@ElementCollection(fetch = FetchType.EAGER)
	private List<String> titles = new ArrayList<>();

	/** A job title containing one of these words is skipped. */
	@ElementCollection(fetch = FetchType.EAGER)
	private List<String> excludedTitles = new ArrayList<>();

	/** A job's place must contain one of these; empty accepts every place. */
	@ElementCollection(fetch = FetchType.EAGER)
	private List<String> locations = new ArrayList<>();

	/** Openings where fewer than this share of the skills they mention are in the CV are not saved. */
	private int minMatchPercent = 20;

	private boolean daily;

	private Instant lastRunAt;

	@Column(length = 2000)
	private String lastRunSummary;

}
