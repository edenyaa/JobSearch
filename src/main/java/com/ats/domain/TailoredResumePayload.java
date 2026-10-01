package com.ats.domain;

import java.util.List;

public record TailoredResumePayload(TailoredCv resume, List<OptimizedBullet> bullets, GapAnalysis gap,
		List<String> rejectedBullets) {
}
