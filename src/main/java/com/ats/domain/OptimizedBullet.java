package com.ats.domain;

public record OptimizedBullet(String originalBullet, String optimizedBullet, String matchedRequirement,
		boolean metricPreserved) {
}
