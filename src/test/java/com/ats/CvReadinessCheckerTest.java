package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.Analysis.Check;
import com.ats.domain.Analysis.CheckResult;
import com.ats.domain.CvReadinessChecker;
import java.util.List;
import org.junit.jupiter.api.Test;

class CvReadinessCheckerTest {

    private final CvReadinessChecker checker = new CvReadinessChecker();

    static String goodCv() {
        return "Dana Levi\ndana@example.com\n+972 50 111 2222\n\nSummary\nBackend developer.\n\nExperience\n"
                + "2021 - 2025 Backend Developer, ShopCo\n- Built services\n\nEducation\nBSc Computer Science 2015 - 2018\n\n"
                + "Skills\nJava, Docker\n\n" + "word ".repeat(250);
    }

    private CheckResult resultOf(List<Check> checks, String id) {
        return checks.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().result();
    }

    @Test
    public void GivenCompleteCv_WhenChecking_ThenEverythingPasses() {
        List<Check> checks = checker.check(goodCv());

        assertEquals(7, checks.size());
        assertTrue(checks.stream().allMatch(c -> c.result() == CheckResult.PASS));
    }

    @Test
    public void GivenEmptyCv_WhenChecking_ThenEverythingFails() {
        List<Check> checks = checker.check("");

        assertEquals(7, checks.size());
        assertTrue(checks.stream().allMatch(c -> c.result() == CheckResult.FAIL));
    }

    @Test
    public void GivenNullCv_WhenChecking_ThenEverythingFails() {
        assertTrue(checker.check(null).stream().allMatch(c -> c.result() == CheckResult.FAIL));
    }

    @Test
    public void GivenCvWithoutEmail_WhenChecking_ThenOnlyEmailFails() {
        List<Check> checks = checker.check(goodCv().replace("dana@example.com", ""));

        assertEquals(CheckResult.FAIL, resultOf(checks, "email"));
        assertEquals(CheckResult.PASS, resultOf(checks, "phone"));
    }

    @Test
    public void GivenCvWithoutPhone_WhenChecking_ThenPhoneFails() {
        List<Check> checks = checker.check(goodCv().replace("+972 50 111 2222", ""));

        assertEquals(CheckResult.FAIL, resultOf(checks, "phone"));
    }

    @Test
    public void GivenLocalPhoneFormat_WhenChecking_ThenPhonePasses() {
        List<Check> checks = checker.check(goodCv().replace("+972 50 111 2222", "(050) 111-2222"));

        assertEquals(CheckResult.PASS, resultOf(checks, "phone"));
    }

    @Test
    public void GivenHeadingsWritteninDifferentCase_WhenChecking_ThenTheyAreStillFound() {
        String cv = goodCv().replace("Experience\n", "WORK EXPERIENCE\n").replace("Education\n", "education:\n")
                .replace("Skills\n", "Technical Skills\n");

        List<Check> checks = checker.check(cv);

        assertEquals(CheckResult.PASS, resultOf(checks, "experience"));
        assertEquals(CheckResult.PASS, resultOf(checks, "education"));
        assertEquals(CheckResult.PASS, resultOf(checks, "skills"));
    }

    @Test
    public void GivenHeadingWordInsideASentence_WhenChecking_ThenItIsNotTreatedAsAHeading() {
        String cv = "I have lots of experience and education and skills in many things. " + "word ".repeat(250);

        List<Check> checks = checker.check(cv);

        assertEquals(CheckResult.FAIL, resultOf(checks, "experience"));
        assertEquals(CheckResult.FAIL, resultOf(checks, "education"));
        assertEquals(CheckResult.FAIL, resultOf(checks, "skills"));
    }

    @Test
    public void GivenCvWithOneYear_WhenChecking_ThenDatesFail() {
        List<Check> checks = checker.check("Developer 2020\n" + "word ".repeat(250));

        assertEquals(CheckResult.FAIL, resultOf(checks, "dates"));
    }

    @Test
    public void GivenVeryShortCv_WhenChecking_ThenLengthFailsAndSaysShort() {
        Check length = checker.check("Experience\nDeveloper 2020 - 2024\n").stream().filter(c -> c.id().equals("length")).findFirst().orElseThrow();

        assertEquals(CheckResult.FAIL, length.result());
        assertTrue(length.detail().contains("short"));
    }

    @Test
    public void GivenVeryLongCv_WhenChecking_ThenLengthFailsAndSaysLong() {
        Check length = checker.check("word ".repeat(1500)).stream().filter(c -> c.id().equals("length")).findFirst().orElseThrow();

        assertEquals(CheckResult.FAIL, length.result());
        assertTrue(length.detail().contains("long"));
    }

}
