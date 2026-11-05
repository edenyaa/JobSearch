package com.ats.controllers;

import com.ats.domain.Company;
import com.ats.dto.CompanyDtos.CompanyRequest;
import com.ats.dto.CompanyDtos.CompanyResponse;
import com.ats.service.CompanyService;
import com.ats.service.JobSearchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/companies")
public class CompanyController {

	private final CompanyService companies;
	private final JobSearchService search;

	public CompanyController(CompanyService companies, JobSearchService search) {
		this.companies = companies;
		this.search = search;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CompanyResponse create(@PathVariable Long userId, @Valid @RequestBody CompanyRequest req) {
		Company c = companies.create(userId, req);
		return respond(userId, c, search.syncCompanyLink(userId, c.getName(), c));
	}

	@GetMapping
	public List<CompanyResponse> list(@PathVariable Long userId) {
		search.mirrorWatched(userId);
		Set<String> searched = search.searchedNames(userId);
		return companies.list(userId).stream()
				.map(c -> respond(userId, c, searched.contains(c.getName().toLowerCase(Locale.ROOT)))).toList();
	}

	@GetMapping("/{id}")
	public CompanyResponse get(@PathVariable Long userId, @PathVariable Long id) {
		Company c = companies.get(userId, id);
		return respond(userId, c, search.searchedNames(userId).contains(c.getName().toLowerCase(Locale.ROOT)));
	}

	@PutMapping("/{id}")
	public CompanyResponse update(@PathVariable Long userId, @PathVariable Long id, @Valid @RequestBody CompanyRequest req) {
		String oldName = companies.get(userId, id).getName();
		Company c = companies.update(userId, id, req);
		return respond(userId, c, search.syncCompanyLink(userId, oldName, c));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long userId, @PathVariable Long id) {
		companies.delete(userId, id);
	}

	private CompanyResponse respond(Long userId, Company c, boolean searched) {
		return CompanyResponse.from(c, companies.jobsOf(userId, c), searched);
	}

}
