package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.FitScore;
import org.junit.jupiter.api.Test;

class FitScoreTest {

    @Test
    public void GivenKeywordMatch_WhenScoring_ThenMostOfItCounts() {
        assertEquals(80, FitScore.fromKeywords(100, "Backend Developer"));
        assertEquals(40, FitScore.fromKeywords(50, "Backend Developer"));
    }

    @Test
    public void GivenJuniorOrStudentTitle_WhenScoring_ThenEntryLevelBonusIsAdded() {
        assertEquals(50, FitScore.fromKeywords(50, "Junior QA Engineer"));
        assertEquals(50, FitScore.fromKeywords(50, "Student Position - Software"));
        assertTrue(FitScore.isEntryLevel("Entry-Level Developer"));
        assertFalse(FitScore.isEntryLevel("Juniper Networks Engineer"));
        assertFalse(FitScore.isEntryLevel(null));
    }

    @Test
    public void GivenPriorityPlace_WhenScoring_ThenBonusIsAddedButNeverAboveHundred() {
        assertEquals(70, FitScore.withPriority(60, true));
        assertEquals(60, FitScore.withPriority(60, false));
        assertEquals(100, FitScore.withPriority(95, true));
    }

}
