package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.AtsLayout;
import com.ats.domain.TailoredCv;
import com.ats.domain.TailoredCv.Education;
import com.ats.domain.TailoredCv.Experience;
import com.ats.domain.TailoredCv.Project;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtsLayoutTest {

    private final TailoredCv full = new TailoredCv("Eden Y", List.of("eden@x.com", "Tel Aviv"), "Backend engineer",
            List.of("Java", "Spring"),
            List.of(new Experience("Developer", "Acme", "2019-2023", List.of("Built APIs"))),
            List.of(new Project("Shop", List.of("Java", "Docker"), List.of("Built checkout"))),
            List.of(new Education("BSc CS", "TAU", "2015-2018")), List.of("Volunteer mentor"));

    @Test
    public void GivenFullCv_WhenRenderingMarkdown_ThenSectionsAreInAtsFriendlyOrder() {
        String markdown = AtsLayout.markdown(full);

        int summary = markdown.indexOf("## SUMMARY");
        int skills = markdown.indexOf("## SKILLS");
        int experience = markdown.indexOf("## EXPERIENCE");
        int projects = markdown.indexOf("## PROJECTS");
        int education = markdown.indexOf("## EDUCATION");
        int additional = markdown.indexOf("## ADDITIONAL");
        assertTrue(markdown.startsWith("# Eden Y\neden@x.com\nTel Aviv\n"));
        assertTrue(summary < skills && skills < experience && experience < projects);
        assertTrue(projects < education && education < additional);
    }

    @Test
    public void GivenFullCv_WhenRenderingMarkdown_ThenContentUsesPlainHeadingsBulletsAndPipes() {
        String markdown = AtsLayout.markdown(full);

        assertTrue(markdown.contains("Java, Spring"));
        assertTrue(markdown.contains("### Developer | Acme | 2019-2023"));
        assertTrue(markdown.contains("- Built APIs"));
        assertTrue(markdown.contains("### Shop | Java, Docker"));
        assertTrue(markdown.contains("BSc CS | TAU | 2015-2018"));
        assertTrue(markdown.contains("- Volunteer mentor"));
    }

    @Test
    public void GivenFullCv_WhenRenderingMarkdown_ThenNoTablesOrDecorationsAreUsed() {
        String markdown = AtsLayout.markdown(full);

        assertFalse(markdown.contains("|---"));
        assertFalse(markdown.contains("<"));
        assertFalse(markdown.contains("!["));
        assertTrue(markdown.chars().allMatch(c -> c < 128));
    }

    @Test
    public void GivenOnlyNameAndSummary_WhenRenderingMarkdown_ThenEmptySectionsAreOmitted() {
        String markdown = AtsLayout.markdown(new TailoredCv("Eden", null, "Engineer", null, null, null, null));

        assertEquals("# Eden\n\n## SUMMARY\nEngineer\n", markdown);
    }

    @Test
    public void GivenBlankParts_WhenJoining_ThenOnlyPresentPartsAreSeparated() {
        assertEquals("a | c", AtsLayout.join("a", " ", null, "c"));
        assertEquals("", AtsLayout.join(null, ""));
    }

}
