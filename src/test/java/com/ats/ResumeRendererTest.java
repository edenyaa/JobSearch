package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.TailoredCv;
import com.ats.domain.TailoredCv.Education;
import com.ats.domain.TailoredCv.Experience;
import com.ats.service.CvTextExtractor;
import com.ats.service.ResumeRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeRendererTest {

    private final ResumeRenderer renderer = new ResumeRenderer();
    private final CvTextExtractor extractor = new CvTextExtractor();

    private String textOf(TailoredCv cv) {
        return extractor.extract(renderer.render(cv), "cv.docx");
    }

    @Test
    public void GivenFullCv_WhenRendering_ThenDocxContainsAllSectionsAndContent() {
        TailoredCv cv = new TailoredCv("Eden Y", List.of("eden@x.com", "Tel Aviv"), "Backend engineer",
                List.of("Java", "Spring"),
                List.of(new Experience("Developer", "Acme", "2019-2023", List.of("Built APIs"))),
                List.of(new Education("BSc CS", "TAU", "2015-2018")), List.of("Volunteer mentor"));

        String text = textOf(cv);

        assertTrue(text.contains("Eden Y"));
        assertTrue(text.contains("eden@x.com"));
        assertTrue(text.contains("SUMMARY"));
        assertTrue(text.contains("Backend engineer"));
        assertTrue(text.contains("SKILLS"));
        assertTrue(text.contains("Java, Spring"));
        assertTrue(text.contains("EXPERIENCE"));
        assertTrue(text.contains("Developer | Acme | 2019-2023"));
        assertTrue(text.contains("• Built APIs"));
        assertTrue(text.contains("EDUCATION"));
        assertTrue(text.contains("BSc CS | TAU | 2015-2018"));
        assertTrue(text.contains("ADDITIONAL"));
        assertTrue(text.contains("• Volunteer mentor"));
    }

    @Test
    public void GivenCvWithOnlyNameAndSummary_WhenRendering_ThenEmptySectionsAreOmitted() {
        TailoredCv cv = new TailoredCv("Eden", null, "Engineer", null, null, null, null);

        String text = textOf(cv);

        assertTrue(text.contains("SUMMARY"));
        assertFalse(text.contains("SKILLS"));
        assertFalse(text.contains("EXPERIENCE"));
        assertFalse(text.contains("EDUCATION"));
        assertFalse(text.contains("ADDITIONAL"));
    }

    @Test
    public void GivenExperienceWithMissingParts_WhenRendering_ThenOnlyPresentPartsAreJoined() {
        TailoredCv cv = new TailoredCv("Eden", null, null, null,
                List.of(new Experience("Developer", null, "2020", List.of())), null, null);

        String text = textOf(cv);

        assertTrue(text.contains("Developer | 2020"));
    }

    @Test
    public void GivenCv_WhenRendering_ThenResultIsAValidDocxThatCanBeReadBack() {
        TailoredCv cv = new TailoredCv("Eden", null, "Engineer", null, null, null, null);

        byte[] bytes = renderer.render(cv);

        assertTrue(bytes.length > 0);
        assertEquals('P', (char) bytes[0]);
        assertEquals('K', (char) bytes[1]);
    }

}
