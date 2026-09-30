package com.ats.dto;

import com.ats.domain.MatchResult;
import java.util.List;

public final class AnalysisDtos {

	private AnalysisDtos() {
	}

	public record CvTextResponse(String text) {
	}

	public record MatchResponse(int coveragePercent, List<String> matchedTerms, List<String> missingTerms) {

		public static MatchResponse from(MatchResult r) {
			return new MatchResponse(r.coveragePercent(), r.matchedTerms(), r.missingTerms());
		}
	}

}
