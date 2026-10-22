package com.ats.service;

import com.ats.domain.Company;
import com.ats.domain.ContactPerson;
import com.ats.domain.Job;
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

	private void apply(Company c, String name, CompanyRequest req) {
		c.setName(name);
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
