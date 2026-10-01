package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.TailoredCv;
import com.ats.domain.TailoredCv.Education;
import com.ats.domain.TailoredCv.Experience;
import java.util.List;
import org.junit.jupiter.api.Test;

class TailoredCvTest {

    private TailoredCv sample() {
        return new TailoredCv("Eden", List.of("eden@x.com"), "Backend engineer", List.of("Java", "Spring"),
                List.of(new Experience("Developer", "Acme", "2019-2023", List.of("Built APIs", "Wrote tests"))),
                List.of(new Education("BSc", "TAU", "2015-2018")), List.of("Volunteer mentor"));
    }

    @Test
    public void GivenNullLists_WhenCreating_ThenTheyBecomeEmptyLists() {
        TailoredCv cv = new TailoredCv("Eden", null, null, null, null, null, null);

        assertTrue(cv.contact().isEmpty());
        assertTrue(cv.skills().isEmpty());
        assertTrue(cv.experience().isEmpty());
        assertTrue(cv.education().isEmpty());
        assertTrue(cv.additional().isEmpty());
    }

    @Test
    public void GivenExperienceWithoutBullets_WhenCreating_ThenBulletsIsEmptyList() {
        Experience experience = new Experience("Dev", "Acme", "2020", null);

        assertTrue(experience.bullets().isEmpty());
    }

    @Test
    public void GivenCv_WhenMapping_ThenEveryTextFieldIsTransformed() {
        TailoredCv mapped = sample().map(String::toUpperCase);

        assertEquals("EDEN", mapped.name());
        assertEquals(List.of("EDEN@X.COM"), mapped.contact());
        assertEquals("BACKEND ENGINEER", mapped.summary());
        assertEquals(List.of("JAVA", "SPRING"), mapped.skills());
        assertEquals("DEVELOPER", mapped.experience().get(0).role());
        assertEquals("ACME", mapped.experience().get(0).company());
        assertEquals(List.of("BUILT APIS", "WROTE TESTS"), mapped.experience().get(0).bullets());
        assertEquals("TAU", mapped.education().get(0).institution());
        assertEquals(List.of("VOLUNTEER MENTOR"), mapped.additional());
    }

    @Test
    public void GivenNullFields_WhenMapping_ThenNullsStayNull() {
        TailoredCv cv = new TailoredCv(null, null, null, null, null, null, null);

        TailoredCv mapped = cv.map(String::toUpperCase);

        assertNull(mapped.name());
        assertNull(mapped.summary());
    }

    @Test
    public void GivenCv_WhenGettingPlainText_ThenAllContentIsIncluded() {
        String text = sample().plainText();

        assertTrue(text.contains("Eden"));
        assertTrue(text.contains("Backend engineer"));
        assertTrue(text.contains("Spring"));
        assertTrue(text.contains("Built APIs"));
        assertTrue(text.contains("TAU"));
        assertTrue(text.contains("Volunteer mentor"));
    }

    @Test
    public void GivenBlankFields_WhenGettingPlainText_ThenTheyAreSkipped() {
        TailoredCv cv = new TailoredCv("Eden", List.of(" "), "", List.of(), List.of(), List.of(), List.of());

        assertEquals("Eden", cv.plainText());
    }

    @Test
    public void GivenCvWithoutContent_WhenCheckingIsEmpty_ThenTrue() {
        TailoredCv onlyName = new TailoredCv("Eden", List.of("a@b.com"), " ", null, null, null, null);

        assertTrue(onlyName.isEmpty());
    }

    @Test
    public void GivenCvWithSummaryOrSkillsOrExperience_WhenCheckingIsEmpty_ThenFalse() {
        assertFalse(sample().isEmpty());
        assertFalse(new TailoredCv(null, null, "Summary", null, null, null, null).isEmpty());
        assertFalse(new TailoredCv(null, null, null, List.of("Java"), null, null, null).isEmpty());
    }

}
