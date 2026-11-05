package com.ats.controllers;

import com.ats.dto.SearchDtos.ImportRequest;
import com.ats.dto.SearchDtos.SettingsRequest;
import com.ats.dto.SearchDtos.SettingsResponse;
import com.ats.dto.SearchDtos.WatchRequest;
import com.ats.dto.SearchDtos.WatchedResponse;
import com.ats.service.JobSearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/search")
public class SearchController {

	private final JobSearchService search;

	public SearchController(JobSearchService search) {
		this.search = search;
	}

	@GetMapping
	public SettingsResponse get(@PathVariable Long userId) {
		return respond(userId);
	}

	@PutMapping
	public SettingsResponse update(@PathVariable Long userId, @RequestBody SettingsRequest req) {
		search.update(userId, req);
		return respond(userId);
	}

	/** Puts the recommended titles, places and thresholds back; the searched companies stay. */
	@PostMapping("/reset")
	public SettingsResponse reset(@PathVariable Long userId) {
		search.resetToRecommended(userId);
		return respond(userId);
	}

	@PostMapping("/run")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SettingsResponse run(@PathVariable Long userId) {
		search.startRun(userId);
		return respond(userId);
	}

	@GetMapping("/companies")
	public List<WatchedResponse> companies(@PathVariable Long userId) {
		// the same companies as the Companies page; id is the company's, board is empty when no page is searched
		return search.searchCompanies(userId).stream()
				.map(sc -> new WatchedResponse(sc.company().getId(), sc.company().getName(),
						sc.watched() == null ? null : sc.watched().getBoard(),
						sc.watched() == null ? null : sc.watched().getSlug(), sc.company().getCareersUrl(), null))
				.toList();
	}

	@PostMapping("/companies")
	@ResponseStatus(HttpStatus.CREATED)
	public WatchedResponse watch(@PathVariable Long userId, @Valid @RequestBody WatchRequest req) {
		JobSearchService.Watched added = search.watch(userId, req.company(), req.name(), req.link());
		return new WatchedResponse(added.row().getId(), added.row().getName(), added.company().getBoard(),
				added.company().getSlug(), added.row().getCareersUrl(), added.openings());
	}

	/** Looks up many companies' career pages in the background; no names means the built-in Israeli list. */
	@PostMapping("/companies/import")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SettingsResponse importCompanies(@PathVariable Long userId, @RequestBody(required = false) ImportRequest req) {
		search.startImport(userId, req == null ? null : req.names());
		return respond(userId);
	}

	@DeleteMapping("/companies/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unwatch(@PathVariable Long userId, @PathVariable Long id) {
		search.unwatch(userId, id);
	}

	private SettingsResponse respond(Long userId) {
		return SettingsResponse.from(search.settings(userId), search.isRunning(userId), search.aggregatorEnabled(),
				search.isImporting(userId), search.importStatus(userId));
	}

}
