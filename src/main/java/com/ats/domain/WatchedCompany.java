package com.ats.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A company career page every search looks at. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "board", "slug" }))
public class WatchedCompany {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private User user;

	@Column(nullable = false)
	private String name;

	/** "greenhouse" or "lever". */
	@Column(nullable = false)
	private String board;

	/** For a "page" board a short fingerprint of the address; otherwise the board's own name for the company. */
	@Column(nullable = false)
	private String slug;

	/** The careers page address, for a "page" board only. */
	@Column(length = 2000)
	private String address;

}
