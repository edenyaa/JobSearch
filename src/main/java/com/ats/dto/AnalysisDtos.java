package com.ats.dto;

import com.ats.domain.Analysis;
import com.ats.domain.MatchResult;
import com.ats.domain.SkillCatalog;
import com.ats.domain.SkillCategory;
import java.util.List;

public final class AnalysisDtos {

	private AnalysisDtos() {
	}

	public record CvTextResponse(String text) {
	}

	public record DetectedSkillResponse(String name, SkillCategory category, String categoryLabel, List<String> keywords) {

		public static DetectedSkillResponse from(SkillCatalog.Detected d) {
			List<String> keywords = d.foundForms().stream().filter(form -> !form.equalsIgnoreCase(d.name())).toList();
			return new DetectedSkillResponse(d.name(), d.category(), d.category().label(), keywords);
		}
	}

	public record TermResponse(String name, SkillCategory category, Analysis.Status status, Analysis.Importance importance) {
	}

	public record KeywordResponse(String term, boolean inCv, int mentions) {
	}

	public record CheckResponse(String id, String label, Analysis.CheckResult result, String detail) {
	}

	public record ComponentResponse(String key, String label, int percent, int weight, String detail) {
	}

	public record CategoryScoreResponse(SkillCategory category, String label, int total, int matched, int inProfile,
			int missing, int percent) {
	}

	public record CategoryCountResponse(SkillCategory category, String label, int count) {
	}

	public record AnalysisResponse(
			int overallScore,
			int coveragePercent,
			int potentialPercent,
			int total,
			int matched,
			int inProfile,
			int missing,
			List<TermResponse> terms,
			List<CategoryScoreResponse> categories,
			List<CategoryCountResponse> cvCategories,
			List<String> extraCvSkills,
			List<KeywordResponse> keywords,
			List<CheckResponse> requirementChecks,
			List<CheckResponse> readinessChecks,
			List<ComponentResponse> components) {

		public static AnalysisResponse from(Analysis a) {
			return new AnalysisResponse(
					a.overallScore(),
					a.coveragePercent(),
					a.potentialPercent(),
					a.total(),
					a.matched(),
					a.inProfile(),
					a.missing(),
					a.terms().stream().map(t -> new TermResponse(t.name(), t.category(), t.status(), t.importance())).toList(),
					a.categories().stream().map(c -> new CategoryScoreResponse(c.category(), c.category().label(), c.total(),
							c.matched(), c.inProfile(), c.missing(), c.percent())).toList(),
					a.cvCategories().stream().map(c -> new CategoryCountResponse(c.category(), c.category().label(), c.count())).toList(),
					a.extraCvSkills(),
					a.keywords().stream().map(k -> new KeywordResponse(k.term(), k.inCv(), k.mentions())).toList(),
					a.requirementChecks().stream().map(c -> new CheckResponse(c.id(), c.label(), c.result(), c.detail())).toList(),
					a.readinessChecks().stream().map(c -> new CheckResponse(c.id(), c.label(), c.result(), c.detail())).toList(),
					a.components().stream().map(c -> new ComponentResponse(c.key(), c.label(), c.percent(), c.weight(), c.detail())).toList());
		}
	}

	public record TailorResponse(
			JobDtos.JobResponse job,
			int coverageBefore,
			int coverageAfter,
			List<String> matchedTerms,
			List<String> missingTerms,
			List<String> unsupportedSkills,
			List<String> missingKeywords,
			List<NamingFix> namingFixes,
			List<BulletChange> bullets,
			List<String> rejectedBullets,
			String resumeMarkdown) {

		public static TailorResponse from(com.ats.service.TailoringResult r) {
			return new TailorResponse(JobDtos.JobResponse.from(r.job()), r.before().coveragePercent(),
					r.after().coveragePercent(), r.after().matchedTerms(), r.after().missingTerms(),
					r.unsupportedSkills(), r.gap().missingKeywords(),
					r.gap().namingMismatches().stream().map(m -> new NamingFix(m.resumeTerm(), m.jobTerm())).toList(),
					r.bullets().stream().map(b -> new BulletChange(b.originalBullet(), b.optimizedBullet(),
							b.matchedRequirement(), b.metricPreserved())).toList(),
					r.rejectedBullets(), r.resumeMarkdown());
		}
	}

	public record NamingFix(String from, String to) {
	}

	public record BulletChange(String originalBullet, String optimizedBullet, String matchedRequirement,
			boolean metricPreserved) {
	}

	public record MatchResponse(int coveragePercent, List<String> matchedTerms, List<String> missingTerms) {

		public static MatchResponse from(MatchResult r) {
			return new MatchResponse(r.coveragePercent(), r.matchedTerms(), r.missingTerms());
		}
	}

}
