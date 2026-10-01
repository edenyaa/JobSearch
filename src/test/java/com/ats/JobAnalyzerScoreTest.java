package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.Analysis;
import com.ats.domain.Analysis.Component;
import com.ats.domain.Analysis.Importance;
import com.ats.domain.JobAnalyzer;
import com.ats.domain.Skill;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobAnalyzerScoreTest {

    private final JobAnalyzer analyzer = new JobAnalyzer(2026);

    private Importance importanceOf(Analysis analysis, String name) {
        return analysis.terms().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow().importance();
    }

    private Component component(Analysis analysis, String key) {
        return analysis.components().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setName(name);
        return skill;
    }

    @Test
    public void GivenInlineNiceToHave_WhenAnalyzing_ThenSkillIsPreferredAndCountsHalf() {
        String job = "Required: Java and Docker.\nKubernetes is a plus.";

        Analysis analysis = analyzer.analyze(job, "Java Docker", List.of());

        assertEquals(Importance.REQUIRED, importanceOf(analysis, "Java"));
        assertEquals(Importance.REQUIRED, importanceOf(analysis, "Docker"));
        assertEquals(Importance.PREFERRED, importanceOf(analysis, "Kubernetes"));
        assertEquals(80, analysis.coveragePercent());
    }

    @Test
    public void GivenNiceToHaveSection_WhenAnalyzing_ThenSkillsUntilTheBlankLineArePreferred() {
        String job = "Requirements:\nJava\nNice to have:\nKubernetes\nTerraform\n\nDocker";

        Analysis analysis = analyzer.analyze(job, "", List.of());

        assertEquals(Importance.REQUIRED, importanceOf(analysis, "Java"));
        assertEquals(Importance.PREFERRED, importanceOf(analysis, "Kubernetes"));
        assertEquals(Importance.PREFERRED, importanceOf(analysis, "Terraform"));
        assertEquals(Importance.REQUIRED, importanceOf(analysis, "Docker"));
    }

    @Test
    public void GivenRequirementsHeadingAfterNiceToHave_WhenAnalyzing_ThenSectionStopsBeingPreferred() {
        String job = "Nice to have:\nKubernetes\nRequirements:\nJava";

        Analysis analysis = analyzer.analyze(job, "", List.of());

        assertEquals(Importance.PREFERRED, importanceOf(analysis, "Kubernetes"));
        assertEquals(Importance.REQUIRED, importanceOf(analysis, "Java"));
    }

    @Test
    public void GivenSkillBothRequiredAndPreferredInPosting_WhenAnalyzing_ThenItIsRequired() {
        Analysis analysis = analyzer.analyze("Java is required.\nJava experience is a plus.", "", List.of());

        assertEquals(Importance.REQUIRED, importanceOf(analysis, "Java"));
    }

    @Test
    public void GivenMissingNiceToHave_WhenAnalyzing_ThenCoverageDropsLessThanForMissingRequiredSkill() {
        Analysis missingPreferred = analyzer.analyze("Java and Docker.\nKubernetes is a plus.", "Java Docker", List.of());
        Analysis missingRequired = analyzer.analyze("Java and Docker and Kubernetes.", "Java Docker", List.of());

        assertTrue(missingPreferred.coveragePercent() > missingRequired.coveragePercent());
    }

    @Test
    public void GivenProfileSkillForNiceToHave_WhenAnalyzing_ThenPotentialIsWeightedToo() {
        Analysis analysis = analyzer.analyze("Java.\nKubernetes is a plus.", "Java", List.of(skill("Kubernetes")));

        assertEquals(67, analysis.coveragePercent());
        assertEquals(100, analysis.potentialPercent());
    }

    @Test
    public void GivenFullSkillCoverageButWeakCv_WhenAnalyzing_ThenOverallScoreIsNotOneHundred() {
        Analysis analysis = analyzer.analyze("We need Java.", "Java", List.of());

        assertEquals(100, analysis.coveragePercent());
        assertEquals(83, analysis.overallScore());
        assertTrue(analysis.overallScore() < 100);
    }

    @Test
    public void GivenStrongCvThatMeetsEverything_WhenAnalyzing_ThenOverallScoreIsOneHundred() {
        String cv = CvReadinessCheckerTest.goodCv() + "\nBackend Developer using Java and Docker";

        Analysis analysis = analyzer.analyze("Backend Developer", "We need Java and Docker.", cv, List.of());

        assertEquals(100, analysis.overallScore());
        assertEquals(List.of("skills", "requirements", "readiness"), analysis.components().stream().map(Component::key).toList());
    }

    @Test
    public void GivenAllComponentsApplicable_WhenAnalyzing_ThenWeightsAreFiftyTwentyTwentyTen() {
        String job = "We need Java. Payment systems matter. Payment systems scale. 3+ years of experience.";
        String cv = CvReadinessCheckerTest.goodCv();

        Analysis analysis = analyzer.analyze("Developer", job, cv, List.of());

        assertEquals(List.of(50, 20, 20, 10), analysis.components().stream().map(Component::weight).toList());
        assertEquals(List.of("skills", "keywords", "requirements", "readiness"),
                analysis.components().stream().map(Component::key).toList());
    }

    @Test
    public void GivenJobWithoutKeywordsOrRequirements_WhenAnalyzing_ThenThoseComponentsAreLeftOut() {
        Analysis analysis = analyzer.analyze("We need Java.", "Java", List.of());

        assertEquals(List.of("skills", "readiness"), analysis.components().stream().map(Component::key).toList());
    }

    @Test
    public void GivenPostingWithRepeatedPhrase_WhenAnalyzing_ThenItAppearsAsAKeywordGapAndLowersTheKeywordComponent() {
        String job = "Payment systems matter. Payment systems scale.";

        Analysis analysis = analyzer.analyze(job, "Java developer", List.of());

        assertEquals("payment systems", analysis.keywords().get(0).term());
        assertFalse(analysis.keywords().get(0).inCv());
        assertEquals(0, component(analysis, "keywords").percent());
    }

    @Test
    public void GivenPostingKeywordPresentInCv_WhenAnalyzing_ThenKeywordComponentIsFull() {
        String job = "Payment systems matter. Payment systems scale.";

        Analysis analysis = analyzer.analyze(job, "I own payment systems", List.of());

        assertEquals(100, component(analysis, "keywords").percent());
    }

    @Test
    public void GivenYearsAskedAndCvTooJunior_WhenAnalyzing_ThenRequirementsComponentDropsAndMentionsIt() {
        Analysis analysis = analyzer.analyze("Developer", "We need Java and 10+ years of experience.",
                "Java developer 2023 - 2025\n" + CvReadinessCheckerTest.goodCv(), List.of());

        assertEquals(50, component(analysis, "requirements").percent());
        assertTrue(analysis.requirementChecks().stream()
                .anyMatch(c -> c.id().equals("years") && c.result() == Analysis.CheckResult.FAIL));
        assertTrue(analysis.requirementChecks().stream()
                .anyMatch(c -> c.id().equals("title") && c.result() == Analysis.CheckResult.PASS));
    }

    @Test
    public void GivenAnalysis_WhenReadingComponents_ThenOverallIsTheWeightedAverageOfThem() {
        Analysis analysis = analyzer.analyze("Developer", "We need Java and Docker. 3+ years.", CvReadinessCheckerTest.goodCv(), List.of());

        double weighted = analysis.components().stream().mapToDouble(c -> (double) c.percent() * c.weight()).sum();
        int weights = analysis.components().stream().mapToInt(Component::weight).sum();
        assertEquals(Math.round(weighted / weights), analysis.overallScore());
    }

}
