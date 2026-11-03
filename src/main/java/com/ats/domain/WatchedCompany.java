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

	@Column(nullable = false)
	private String slug;

}
