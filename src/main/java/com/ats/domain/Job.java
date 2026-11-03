package com.ats.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Job {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private User user;

	/** The posting's own job id; when the posting has none, a generated "GEN-n" number is assigned. */
	@Column(nullable = false)
	private String jobNumber;

	private String title;
	private String company;

	@Column(length = 10000)
	private String description;

	@Column(length = 2000)
	private String link;

	private LocalDate appliedDate;

	/** Where the job is, as the posting says (set by the job search). */
	private String location;

	/** How well the job fits the user's CV, 0 to 100 (set by the job search). */
	private Integer fitScore;

	@Column(length = 1000)
	private String fitNote;

	/** True when the job is in one of the places the user ranks first. */
	private Boolean priorityPlace;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobStatus status = JobStatus.APPLIED;

	@Column(length = 4000)
	private String notes;

	/** The resume tailored to this job (derived from the user's main CV). */
	private String tailoredResumeFileName;
	private String tailoredResumeContentType;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(length = 10_485_760)
	private byte[] tailoredResume;

}
