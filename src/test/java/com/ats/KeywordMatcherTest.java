package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.KeywordMatcher;
import com.ats.domain.MatchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordMatcherTest {

    private final KeywordMatcher matcher = new KeywordMatcher();

    @Test
    public void GivenTermsInJobAndCv_WhenMatching_ThenAllAreMatchedWithFullCoverage() {
        String job = "We need Java and Spring experience";
        String cv = "Built services with Java and Spring Boot";

        MatchResult result = matcher.match(job, cv, List.of("Java", "Spring"));

        assertEquals(100, result.coveragePercent());
        assertEquals(List.of("Java", "Spring"), result.matchedTerms());
        assertTrue(result.missingTerms().isEmpty());
    }

    @Test
    public void GivenTermInJobButNotInCv_WhenMatching_ThenTermIsMissing() {
        String job = "Looking for Docker and Java";
        String cv = "Java developer";

        MatchResult result = matcher.match(job, cv, List.of("Java", "Docker"));

        assertEquals(50, result.coveragePercent());
        assertEquals(List.of("Java"), result.matchedTerms());
        assertEquals(List.of("Docker"), result.missingTerms());
    }

    @Test
    public void GivenTermNotInJob_WhenMatching_ThenItIsIgnored() {
        String job = "Looking for Java";
        String cv = "Java and Python developer";

        MatchResult result = matcher.match(job, cv, List.of("Java", "Python"));

        assertEquals(List.of("Java"), result.matchedTerms());
        assertTrue(result.missingTerms().isEmpty());
    }

    @Test
    public void GivenJavaTerm_WhenJobOnlyMentionsJavaScript_ThenNoMatch() {
        String job = "Strong JavaScript skills";
        String cv = "Java developer";

        MatchResult result = matcher.match(job, cv, List.of("Java"));

        assertEquals(0, result.coveragePercent());
        assertTrue(result.matchedTerms().isEmpty());
        assertTrue(result.missingTerms().isEmpty());
    }

    @Test
    public void GivenDifferentCase_WhenMatching_ThenMatchIsCaseInsensitive() {
        String job = "SPRING BOOT required";
        String cv = "worked with spring boot";

        MatchResult result = matcher.match(job, cv, List.of("Spring Boot"));

        assertEquals(100, result.coveragePercent());
    }

    @Test
    public void GivenTermsWithSpecialCharacters_WhenMatching_ThenTheyAreMatchedLiterally() {
        String job = "C++, C# and .NET experience";
        String cv = "I know C++ and .NET";

        MatchResult result = matcher.match(job, cv, List.of("C++", "C#", ".NET"));

        assertEquals(List.of("C++", ".NET"), result.matchedTerms());
        assertEquals(List.of("C#"), result.missingTerms());
    }

    @Test
    public void GivenDuplicateAndBlankTerms_WhenMatching_ThenTheyAreCountedOnce() {
        String job = "Java developer";
        String cv = "Java";

        MatchResult result = matcher.match(job, cv, java.util.Arrays.asList("Java", "java", " ", null, ""));

        assertEquals(List.of("Java"), result.matchedTerms());
        assertEquals(100, result.coveragePercent());
    }

    @Test
    public void GivenNullInputs_WhenMatching_ThenEmptyResultWithZeroCoverage() {
        MatchResult result = matcher.match(null, null, null);

        assertEquals(0, result.coveragePercent());
        assertTrue(result.matchedTerms().isEmpty());
        assertTrue(result.missingTerms().isEmpty());
    }

    @Test
    public void GivenTermAtTextBoundariesAndNextToPunctuation_WhenMatching_ThenItIsFound() {
        String job = "(Java), SQL.";
        String cv = "java;sql";

        MatchResult result = matcher.match(job, cv, List.of("Java", "SQL"));

        assertEquals(100, result.coveragePercent());
    }

    @Test
    public void GivenThreeRelevantTermsOneMatched_WhenMatching_ThenCoverageIsRoundedDown() {
        String job = "Java Spring Docker";
        String cv = "Java";

        MatchResult result = matcher.match(job, cv, List.of("Java", "Spring", "Docker"));

        assertEquals(33, result.coveragePercent());
    }

}
