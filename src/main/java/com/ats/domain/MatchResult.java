package com.ats.domain;

import java.util.List;

public record MatchResult(int coveragePercent, List<String> matchedTerms, List<String> missingTerms) {
}
