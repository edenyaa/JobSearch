package com.ats.service;

import com.ats.domain.Company;
import com.ats.domain.ContactPerson;
import com.ats.domain.Job;
import com.ats.domain.PostingFilter;
import com.ats.dto.CompanyDtos.CompanyRequest;
import com.ats.dto.CompanyDtos.ContactBody;
import com.ats.repository.CompanyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Companies the user tracks. A company's jobs are the user's jobs whose company text matches
 * the company name (ignoring case), so jobs themselves need no extra link.
 */
@Service
@Transactional
public class CompanyService {

	private final CompanyRepository companies;
	private final UserService users;
	private final JobService jobs;

	public CompanyService(CompanyRepository companies, UserService users, JobService jobs) {
		this.companies = companies;
		this.users = users;
		this.jobs = jobs;
	}

	public Company create(Long userId, CompanyRequest req) {
		String name = req.name().trim();
		if (companies.existsByUserIdAndNameIgnoreCase(userId, name)) {
			throw new ConflictException("Company " + name + " already exists");
		}
		Company c = new Company();
		c.setUser(users.get(userId));
		apply(c, name, req);
		return companies.save(c);
	}

	@Transactional(readOnly = true)
	public List<Company> list(Long userId) {
		return companies.findByUserIdOrderByNameAsc(userId);
	}

	@Transactional(readOnly = true)
	public Company get(Long userId, Long id) {
		return companies.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Company", id));
	}

	public Company update(Long userId, Long id, CompanyRequest req) {
		Company c = get(userId, id);
		String name = req.name().trim();
		if (companies.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
			throw new ConflictException("Company " + name + " already exists");
		}
		apply(c, name, req);
		return c;
	}

	public void delete(Long userId, Long id) {
		companies.delete(get(userId, id));
	}

	/** The user's jobs submitted to this company, newest first. */
	@Transactional(readOnly = true)
	public List<Job> jobsOf(Long userId, Company c) {
		return jobs.list(userId, null).stream()
				.filter(j -> j.getCompany() != null && j.getCompany().trim().equalsIgnoreCase(c.getName()))
				.toList();
	}

	/**
	 * Makes sure a company with this name exists. A careers link is stored when the company has none, or always
	 * when {@code overwrite} is set (the user typed it).
	 */
	public Company ensure(Long userId, String name, String careersUrl, boolean overwrite) {
		String trimmed = name.trim();
		Company c = companies.findByUserIdAndNameIgnoreCase(userId, trimmed).orElse(null);
		if (c == null) {
			c = new Company();
			c.setUser(users.get(userId));
			c.setName(trimmed);
			c.setCareersUrl(careersUrl);
			return companies.save(c);
		}
		if (careersUrl != null && (overwrite || c.getCareersUrl() == null || c.getCareersUrl().isBlank())) {
			c.setCareersUrl(careersUrl);
			return companies.save(c);
		}
		return c;
	}

	/** Stops the company being linked to a careers page; the company itself stays. */
	public void clearCareersUrl(Long userId, Long id) {
		Company c = get(userId, id);
		c.setCareersUrl(null);
		companies.save(c);
	}

	/** A pasted careers link: advertising parameters dropped, http(s) only, empty means none. */
	static String cleanLink(String link) {
		if (link == null || link.isBlank()) {
			return null;
		}
		String cleaned = PostingFilter.cleanUrl(link.strip());
		if (!cleaned.matches("(?i)https?://\\S+")) {
			throw new ValidationException("The careers page link must start with http:// or https://");
		}
		if (cleaned.length() > 2000) {
			throw new ValidationException("That link is too long even without its tracking parameters.");
		}
		return cleaned;
	}

	private void apply(Company c, String name, CompanyRequest req) {
		c.setName(name);
		c.setCareersUrl(cleanLink(req.careersUrl()));
		c.setContacts(req.contacts() == null ? new ArrayList<>()
				: req.contacts().stream().map(this::toContact).filter(Objects::nonNull)
						.collect(Collectors.toCollection(ArrayList::new)));
	}

	private ContactPerson toContact(ContactBody body) {
		if (body == null) {
			return null;
		}
		ContactPerson p = new ContactPerson();
		p.setName(blankToNull(body.name()));
		p.setWhereKnown(blankToNull(body.whereKnown()));
		p.setLinkedin(blankToNull(body.linkedin()));
		p.setPhone(blankToNull(body.phone()));
		p.setDescription(blankToNull(body.description()));
		boolean empty = p.getName() == null && p.getWhereKnown() == null && p.getLinkedin() == null
				&& p.getPhone() == null && p.getDescription() == null;
		return empty ? null : p;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
