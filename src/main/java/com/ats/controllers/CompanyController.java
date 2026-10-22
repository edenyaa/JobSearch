package com.ats.controllers;

import com.ats.domain.Company;
import com.ats.dto.CompanyDtos.CompanyRequest;
import com.ats.dto.CompanyDtos.CompanyResponse;
import com.ats.service.CompanyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/companies")
public class CompanyController {

	private final CompanyService companies;

	public CompanyController(CompanyService companies) {
		this.companies = companies;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CompanyResponse create(@PathVariable Long userId, @Valid @RequestBody CompanyRequest req) {
		return respond(userId, companies.create(userId, req));
	}

	@GetMapping
	public List<CompanyResponse> list(@PathVariable Long userId) {
		return companies.list(userId).stream().map(c -> respond(userId, c)).toList();
	}

	@GetMapping("/{id}")
	public CompanyResponse get(@PathVariable Long userId, @PathVariable Long id) {
		return respond(userId, companies.get(userId, id));
	}

	@PutMapping("/{id}")
	public CompanyResponse update(@PathVariable Long userId, @PathVariable Long id, @Valid @RequestBody CompanyRequest req) {
		return respond(userId, companies.update(userId, id, req));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long userId, @PathVariable Long id) {
		companies.delete(userId, id);
	}

	private CompanyResponse respond(Long userId, Company c) {
		return CompanyResponse.from(c, companies.jobsOf(userId, c));
	}

}
