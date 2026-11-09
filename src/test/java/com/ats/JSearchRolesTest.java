package com.ats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ats.service.JobSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;

class JSearchRolesTest {

    @Test
    public void GivenRecommendedTitles_WhenMakingJobBoardSearches_ThenEveryTitleBecomesAUsefulRole() {
        List<String> roles = JobSearchService.jsearchRoles(List.of("qa", "automation", "developer", "software engineer",
                "backend", "ai", "machine learning", "llm", "prompt", "junior", "student", "intern", "trainee",
                "graduate", "entry level", "full stack"));

        assertEquals(List.of("qa", "automation", "software developer", "software engineer",
                "backend developer", "AI engineer", "machine learning engineer", "LLM engineer", "prompt engineer",
                "junior software developer", "student software developer", "intern software developer",
                "trainee software developer", "graduate software developer", "entry level software developer",
                "full stack developer"), roles);
    }

    @Test
    public void GivenRepeatedAndBlankTitles_WhenMakingSearches_ThenRepeatsAndBlanksAreDropped() {
        assertEquals(List.of("qa", "devops"), JobSearchService.jsearchRoles(List.of("qa", "QA", " ", "devops")));
    }
}
