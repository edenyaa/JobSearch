package com.ats.service;

import com.ats.domain.GapAnalysis;
import com.ats.domain.Job;
import com.ats.domain.MatchResult;
import com.ats.domain.OptimizedBullet;
import java.util.List;

public record TailoringResult(Job job, MatchResult before, MatchResult after, List<String> unsupportedSkills,
		GapAnalysis gap, List<OptimizedBullet> bullets, List<String> rejectedBullets, String resumeMarkdown) {
}
