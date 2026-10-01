package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.Analysis;
import com.ats.domain.Analysis.CategoryCount;
import com.ats.domain.Analysis.CategoryScore;
import com.ats.domain.Analysis.Status;
import com.ats.domain.Analysis.Term;
import com.ats.domain.JobAnalyzer;
import com.ats.domain.Skill;
import com.ats.domain.SkillCategory;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobAnalyzerTest {

    private final JobAnalyzer analyzer = new JobAnalyzer();

    private Skill skill(String name, String... keywords) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setKeywords(Arrays.asList(keywords));
        return skill;
    }

    private Status statusOf(Analysis analysis, String name) {
        return analysis.terms().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow().status();
    }

    @Test
    public void GivenAllJobSkillsInCv_WhenAnalyzing_ThenEverythingIsMatchedWithFullCoverage() {
        Analysis analysis = analyzer.analyze("We need Java and Docker", "Built Java services with Docker", List.of());

        assertEquals(2, analysis.total());
        assertEquals(2, analysis.matched());
        assertEquals(100, analysis.coveragePercent());
        assertEquals(100, analysis.potentialPercent());
    }

    @Test
    public void GivenSkillInProfileButNotInCv_WhenAnalyzing_ThenItIsInProfileAndPotentialIsHigher() {
        Analysis analysis = analyzer.analyze("Java and Kubernetes", "Java developer", List.of(skill("Kubernetes")));

        assertEquals(Status.MATCHED, statusOf(analysis, "Java"));
        assertEquals(Status.IN_PROFILE, statusOf(analysis, "Kubernetes"));
        assertEquals(50, analysis.coveragePercent());
        assertEquals(100, analysis.potentialPercent());
        assertEquals(1, analysis.inProfile());
    }

    @Test
    public void GivenSkillNeitherInCvNorProfile_WhenAnalyzing_ThenItIsMissing() {
        Analysis analysis = analyzer.analyze("Java and Rust", "Java developer", List.of());

        assertEquals(Status.MISSING, statusOf(analysis, "Rust"));
        assertEquals(1, analysis.missing());
        assertEquals(50, analysis.coveragePercent());
        assertEquals(50, analysis.potentialPercent());
    }

    @Test
    public void GivenThreeRequirements_WhenAnalyzing_ThenPercentagesAreRounded() {
        Analysis analysis = analyzer.analyze("Java Docker Kubernetes", "Java", List.of(skill("Docker")));

        assertEquals(33, analysis.coveragePercent());
        assertEquals(67, analysis.potentialPercent());
    }

    @Test
    public void GivenJobWithoutKnownSkills_WhenAnalyzing_ThenResultIsEmptyWithZeroCoverage() {
        Analysis analysis = analyzer.analyze("We sell fresh bread", "Java developer", List.of());

        assertEquals(0, analysis.total());
        assertEquals(0, analysis.coveragePercent());
        assertTrue(analysis.terms().isEmpty());
        assertTrue(analysis.categories().isEmpty());
    }

    @Test
    public void GivenUserDefinedSkillOutsideCatalog_WhenInJobAndCv_ThenItIsMatchedInOwnCategory() {
        Analysis analysis = analyzer.analyze("Salesforce experience required", "I used Salesforce daily",
                List.of(skill("Salesforce")));

        Term term = analysis.terms().get(0);
        assertEquals("Salesforce", term.name());
        assertEquals(SkillCategory.OTHER, term.category());
        assertEquals(Status.MATCHED, term.status());
    }

    @Test
    public void GivenUserDefinedSkillOutsideCatalog_WhenNotInCv_ThenItIsInProfile() {
        Analysis analysis = analyzer.analyze("Salesforce experience required", "Java developer",
                List.of(skill("Salesforce")));

        assertEquals(Status.IN_PROFILE, analysis.terms().get(0).status());
    }

    @Test
    public void GivenUserKeywordThatIsACatalogAlias_WhenJobNeedsTheCanonicalSkill_ThenItIsInProfile() {
        Analysis analysis = analyzer.analyze("Kubernetes required", "", List.of(skill("Java", "K8s")));

        assertEquals(Status.IN_PROFILE, statusOf(analysis, "Kubernetes"));
    }

    @Test
    public void GivenCvUsesAlias_WhenJobUsesCanonicalName_ThenItIsMatched() {
        Analysis analysis = analyzer.analyze("PostgreSQL required", "Worked with Postgres", List.of());

        assertEquals(Status.MATCHED, statusOf(analysis, "PostgreSQL"));
    }

    @Test
    public void GivenDifferentLetterCase_WhenAnalyzing_ThenMatchingIgnoresCase() {
        Analysis analysis = analyzer.analyze("JAVA required", "java developer", List.of());

        assertEquals(100, analysis.coveragePercent());
    }

    @Test
    public void GivenUserSkillAlsoRequiredByJob_WhenAnalyzing_ThenItIsCountedOnce() {
        Analysis analysis = analyzer.analyze("Java", "", List.of(skill("Java")));

        assertEquals(1, analysis.total());
        assertEquals(Status.IN_PROFILE, statusOf(analysis, "Java"));
    }

    @Test
    public void GivenSkillsInDifferentCategories_WhenAnalyzing_ThenEachCategoryHasItsOwnScoreInEnumOrder() {
        Analysis analysis = analyzer.analyze("Java, Python, Docker, Agile", "Java Docker", List.of());

        List<CategoryScore> scores = analysis.categories();
        assertEquals(List.of(SkillCategory.LANGUAGES, SkillCategory.CLOUD_DEVOPS, SkillCategory.PRACTICES),
                scores.stream().map(CategoryScore::category).toList());
        assertEquals(2, scores.get(0).total());
        assertEquals(1, scores.get(0).matched());
        assertEquals(50, scores.get(0).percent());
        assertEquals(100, scores.get(1).percent());
        assertEquals(0, scores.get(2).percent());
        assertEquals(1, scores.get(2).missing());
    }

    @Test
    public void GivenCvSkills_WhenAnalyzing_ThenCvCompositionCountsSkillsPerCategory() {
        Analysis analysis = analyzer.analyze("Java", "Java Python Docker", List.of());

        List<CategoryCount> counts = analysis.cvCategories();
        assertEquals(SkillCategory.LANGUAGES, counts.get(0).category());
        assertEquals(2, counts.get(0).count());
        assertEquals(SkillCategory.CLOUD_DEVOPS, counts.get(1).category());
        assertEquals(1, counts.get(1).count());
    }

    @Test
    public void GivenCvSkillsTheJobDoesNotAskFor_WhenAnalyzing_ThenTheyAreListedAsExtras() {
        Analysis analysis = analyzer.analyze("Java", "Java and Docker and Git", List.of());

        assertEquals(List.of("Docker", "Git"), analysis.extraCvSkills());
    }

    @Test
    public void GivenNullInputs_WhenAnalyzing_ThenEmptyAnalysisIsReturned() {
        Analysis analysis = analyzer.analyze(null, null, List.of());

        assertEquals(0, analysis.total());
        assertTrue(analysis.terms().isEmpty());
        assertTrue(analysis.cvCategories().isEmpty());
        assertTrue(analysis.extraCvSkills().isEmpty());
    }

    @Test
    public void GivenBlankAndNullKeywords_WhenAnalyzing_ThenTheyAreIgnored() {
        Analysis analysis = analyzer.analyze("Java", "Java", List.of(skill("Java", "", "  ", null)));

        assertEquals(1, analysis.total());
        assertEquals(100, analysis.coveragePercent());
    }

    @Test
    public void GivenAnalysis_WhenSummingStatuses_ThenCountsAddUpToTotal() {
        Analysis analysis = analyzer.analyze("Java Docker Kubernetes Rust", "Java", List.of(skill("Docker")));

        assertEquals(analysis.total(), analysis.matched() + analysis.inProfile() + analysis.missing());
        assertEquals(4, analysis.total());
    }

}
