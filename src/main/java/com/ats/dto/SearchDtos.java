package com.ats.dto;

import com.ats.domain.SearchSettings;
import com.ats.domain.WatchedCompany;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class SearchDtos {

	private SearchDtos() {
	}

	public record SettingsRequest(
			List<String> titles,
			List<String> excludedTitles,
			List<String> locations,
			List<String> priorityLocations,
			int minMatchPercent,
			int minFitPercent,
			boolean daily) {
	}

	public record SettingsResponse(
			List<String> titles,
			List<String> excludedTitles,
			List<String> locations,
			List<String> priorityLocations,
			int minMatchPercent,
			int minFitPercent,
			boolean daily,
			Instant lastRunAt,
			String lastRunSummary,
			boolean running,
			boolean aggregatorEnabled,
			boolean importing,
			String importStatus) {

		public static SettingsResponse from(SearchSettings s, boolean running, boolean aggregatorEnabled, boolean importing,
				String importStatus) {
			return new SettingsResponse(List.copyOf(s.getTitles()), List.copyOf(s.getExcludedTitles()),
					List.copyOf(s.getLocations()), List.copyOf(s.getPriorityLocations()), s.getMinMatchPercent(),
					s.minFit(), s.isDaily(), s.getLastRunAt(),
					s.getLastRunSummary(), running, aggregatorEnabled, importing, importStatus);
		}
	}

	/** Company names to look up; empty means the built-in list of Israeli tech companies. */
	public record ImportRequest(List<String> names) {
	}

	/** A company name, or the link of its Greenhouse or Lever career page. */
	public record WatchRequest(@NotBlank String company) {
	}

	public record WatchedResponse(Long id, String name, String board, String slug) {

		public static WatchedResponse from(WatchedCompany w) {
			return new WatchedResponse(w.getId(), w.getName(), w.getBoard(), w.getSlug());
		}
	}

}
