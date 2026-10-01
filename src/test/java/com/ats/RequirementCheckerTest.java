package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.Analysis.Check;
import com.ats.domain.Analysis.CheckResult;
import com.ats.domain.RequirementChecker;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RequirementCheckerTest {

    private final RequirementChecker checker = new RequirementChecker(2026);

    private Optional<Check> find(List<Check> checks, String id) {
        return checks.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    private static final String CV_WITH_SIX_YEARS = "Backend Developer, ShopCo\n2021 - 2025\nJunior Developer, WebWorks\n2019 - 2021\n";

    @Test
    public void GivenEnoughDatedExperience_WhenCheckingYears_ThenItPassesWithTheEstimate() {
        Check years = find(checker.check(null, "We need 5+ years of Java", CV_WITH_SIX_YEARS), "years").orElseThrow();

        assertEquals(CheckResult.PASS, years.result());
        assertTrue(years.detail().contains("about 6 years"));
    }

    @Test
    public void GivenTooLittleExperience_WhenCheckingYears_ThenItFailsAndSaysWhatIsAsked() {
        Check years = find(checker.check(null, "We need 8 years of experience", CV_WITH_SIX_YEARS), "years").orElseThrow();

        assertEquals(CheckResult.FAIL, years.result());
        assertTrue(years.detail().contains("8+ years"));
    }

    @Test
    public void GivenCvWithoutDates_WhenCheckingYears_ThenResultIsUnknown() {
        Check years = find(checker.check(null, "5 years of experience", "Java developer"), "years").orElseThrow();

        assertEquals(CheckResult.UNKNOWN, years.result());
    }

    @Test
    public void GivenCurrentJob_WhenCheckingYears_ThenPresentCountsUpToTheCurrentYear() {
        Check years = find(checker.check(null, "4 years required", "Developer 2022 - Present"), "years").orElseThrow();

        assertEquals(CheckResult.PASS, years.result());
        assertTrue(years.detail().contains("about 4 years"));
    }

    @Test
    public void GivenEducationDates_WhenCheckingYears_ThenTheyAreNotCountedAsExperience() {
        String cv = "BSc Computer Science, TAU\n2015 - 2018\nDeveloper 2020 - 2024\n";

        Check years = find(checker.check(null, "6 years of experience", cv), "years").orElseThrow();

        assertEquals(CheckResult.FAIL, years.result());
        assertTrue(years.detail().contains("about 4 years"));
    }

    @Test
    public void GivenOverlappingJobs_WhenCheckingYears_ThenOverlapIsCountedOnce() {
        String cv = "Developer 2019 - 2022\nConsultant 2020 - 2023\n";

        Check years = find(checker.check(null, "3 years", cv), "years").orElseThrow();

        assertTrue(years.detail().contains("about 4 years"));
    }

    @Test
    public void GivenRangeOfYears_WhenCheckingYears_ThenTheLowerNumberIsUsed() {
        Check years = find(checker.check(null, "3-5 years of experience", CV_WITH_SIX_YEARS), "years").orElseThrow();

        assertTrue(years.detail().contains("3+ years"));
    }

    @Test
    public void GivenLargestRequirementInPosting_WhenCheckingYears_ThenTheLargestIsUsed() {
        Check years = find(checker.check(null, "2 years of Docker, 7 years of Java", CV_WITH_SIX_YEARS), "years").orElseThrow();

        assertTrue(years.detail().contains("7+ years"));
    }

    @Test
    public void GivenNoYearsInPosting_WhenChecking_ThenThereIsNoYearsCheck() {
        assertTrue(find(checker.check(null, "We need Java", CV_WITH_SIX_YEARS), "years").isEmpty());
    }

    @Test
    public void GivenDegreeRequiredAndCvHasOne_WhenChecking_ThenEducationPasses() {
        Check education = find(checker.check(null, "Bachelor's degree required", "BSc Computer Science"), "education").orElseThrow();

        assertEquals(CheckResult.PASS, education.result());
    }

    @Test
    public void GivenDegreeRequiredAndCvHasNone_WhenChecking_ThenEducationFails() {
        Check education = find(checker.check(null, "A degree in computer science", "Self taught developer"), "education").orElseThrow();

        assertEquals(CheckResult.FAIL, education.result());
    }

    @Test
    public void GivenNoDegreeInPosting_WhenChecking_ThenThereIsNoEducationCheck() {
        assertTrue(find(checker.check(null, "We need Java", "BSc"), "education").isEmpty());
    }

    @Test
    public void GivenMostTitleWordsInCv_WhenChecking_ThenTitleCheckPassesAndNamesTheMissingWord() {
        Check title = find(checker.check("Senior Backend Engineer", "Java", "Backend Engineer at Acme"), "title").orElseThrow();

        assertEquals(CheckResult.PASS, title.result());
        assertTrue(title.detail().contains("senior"));
    }

    @Test
    public void GivenTitleWordsAllInCv_WhenChecking_ThenTitleCheckPassesWithoutWarning() {
        Check title = find(checker.check("Backend Engineers", "Java", "backend engineer"), "title").orElseThrow();

        assertEquals(CheckResult.PASS, title.result());
        assertFalse(title.detail().contains("does not mention"));
    }

    @Test
    public void GivenTitleWordsMostlyMissing_WhenChecking_ThenTitleCheckFails() {
        Check title = find(checker.check("Senior Backend Engineer", "Java", "Pastry chef"), "title").orElseThrow();

        assertEquals(CheckResult.FAIL, title.result());
    }

    @Test
    public void GivenBlankOrMissingTitle_WhenChecking_ThenThereIsNoTitleCheck() {
        assertTrue(find(checker.check(null, "Java", "cv"), "title").isEmpty());
        assertTrue(find(checker.check("  ", "Java", "cv"), "title").isEmpty());
    }

    @Test
    public void GivenAllNullInputs_WhenChecking_ThenNoChecksAreReturned() {
        assertTrue(checker.check(null, null, null).isEmpty());
    }

}
