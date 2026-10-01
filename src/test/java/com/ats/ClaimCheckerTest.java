package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.ClaimChecker;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimCheckerTest {

    private final ClaimChecker checker = new ClaimChecker();

    @Test
    public void GivenSkillsPresentInCv_WhenChecking_ThenNothingIsUnsupported() {
        String cv = "Built services with Java and Spring";

        List<String> unsupported = checker.unsupported(List.of("Java", "Spring"), cv, List.of());

        assertTrue(unsupported.isEmpty());
    }

    @Test
    public void GivenSkillKnownFromUsersSkillList_WhenChecking_ThenItIsSupportedEvenIfNotInCv() {
        List<String> unsupported = checker.unsupported(List.of("kubernetes"), "Java developer", List.of("Kubernetes"));

        assertTrue(unsupported.isEmpty());
    }

    @Test
    public void GivenSkillNowhereInCvOrKnownTerms_WhenChecking_ThenItIsReportedUnsupported() {
        List<String> unsupported = checker.unsupported(List.of("Java", "Rust"), "Java developer", List.of());

        assertEquals(List.of("Rust"), unsupported);
    }

    @Test
    public void GivenSkillThatOnlyAppearsInsideAnotherWord_WhenChecking_ThenItIsUnsupported() {
        List<String> unsupported = checker.unsupported(List.of("Java"), "JavaScript developer", List.of());

        assertEquals(List.of("Java"), unsupported);
    }

    @Test
    public void GivenDuplicatesBlanksAndNulls_WhenChecking_ThenTheyAreIgnoredOrCountedOnce() {
        List<String> claimed = Arrays.asList("Rust", "rust", " ", null, "");

        List<String> unsupported = checker.unsupported(claimed, "cv", List.of());

        assertEquals(List.of("Rust"), unsupported);
    }

    @Test
    public void GivenClaimWithParenthesisWhereInnerPartIsInCv_WhenChecking_ThenItIsSupported() {
        String cv = "Set up CI with GitHub Actions";

        List<String> unsupported = checker.unsupported(List.of("CI/CD (GitHub Actions)"), cv, List.of());

        assertTrue(unsupported.isEmpty());
    }

    @Test
    public void GivenClaimWithParenthesisWhereBasePartIsInCv_WhenChecking_ThenItIsSupported() {
        String cv = "Worked with PostgreSQL daily";

        List<String> unsupported = checker.unsupported(List.of("PostgreSQL (advanced tuning)"), cv, List.of());

        assertTrue(unsupported.isEmpty());
    }

    @Test
    public void GivenClaimWithParenthesisWhereNeitherPartIsKnown_WhenChecking_ThenItIsUnsupported() {
        List<String> unsupported = checker.unsupported(List.of("Kubernetes (EKS)"), "Java developer", List.of());

        assertEquals(List.of("Kubernetes (EKS)"), unsupported);
    }

    @Test
    public void GivenNullInputs_WhenChecking_ThenResultIsEmpty() {
        assertTrue(checker.unsupported(null, null, null).isEmpty());
        assertEquals(List.of("Go"), checker.unsupported(List.of("Go"), null, null));
    }

}
