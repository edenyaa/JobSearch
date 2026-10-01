package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.GapAnalysis.NamingMismatch;
import com.ats.domain.TailoredCv;
import com.ats.domain.TailoredCv.Experience;
import com.ats.domain.TailoredCv.Project;
import com.ats.domain.TerminologyNormalizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerminologyNormalizerTest {

    private final TerminologyNormalizer normalizer = new TerminologyNormalizer();
    private final List<NamingMismatch> postgres = List.of(new NamingMismatch("PostgreSQL", "Postgres"));

    @Test
    public void GivenAliasInText_WhenNormalizing_ThenItUsesTheJobSpelling() {
        assertEquals("Tuned PostgreSQL queries", normalizer.normalize("Tuned postgres queries", postgres));
    }

    @Test
    public void GivenFullNameAlreadyPresent_WhenNormalizing_ThenItIsNotDamaged() {
        assertEquals("Tuned PostgreSQL queries", normalizer.normalize("Tuned PostgreSQL queries", postgres));
    }

    @Test
    public void GivenNoMismatches_WhenNormalizing_ThenTextIsUnchanged() {
        assertEquals("Tuned Postgres", normalizer.normalize("Tuned Postgres", List.of()));
        assertNull(normalizer.normalize((String) null, postgres));
    }

    @Test
    public void GivenSpecialCharactersInTerm_WhenNormalizing_ThenReplacementIsLiteral() {
        List<NamingMismatch> ci = List.of(new NamingMismatch("CI/CD", "CI CD"));

        assertEquals("Built CI/CD pipelines", normalizer.normalize("Built CI CD pipelines", ci));
    }

    @Test
    public void GivenCv_WhenNormalizing_ThenEverySectionIsUpdatedAndSkillsAreDeduplicated() {
        TailoredCv cv = new TailoredCv("Eden", List.of("a@b.c"), "Knows Postgres",
                List.of("Postgres", "PostgreSQL", "Java"),
                List.of(new Experience("Dev", "Acme", "2020", List.of("Tuned Postgres"))),
                List.of(new Project("Shop", List.of("Postgres"), List.of("Stored orders in Postgres"))),
                List.of(), List.of("Postgres club"));

        TailoredCv result = normalizer.normalize(cv, postgres);

        assertEquals("Knows PostgreSQL", result.summary());
        assertEquals(List.of("PostgreSQL", "Java"), result.skills());
        assertEquals("Tuned PostgreSQL", result.experience().get(0).bullets().get(0));
        assertEquals(List.of("PostgreSQL"), result.projects().get(0).technologies());
        assertEquals("Stored orders in PostgreSQL", result.projects().get(0).bullets().get(0));
        assertEquals(List.of("PostgreSQL club"), result.additional());
        assertEquals("Eden", result.name());
    }

    @Test
    public void GivenNoMismatches_WhenNormalizingCv_ThenTheSameCvIsReturned() {
        TailoredCv cv = new TailoredCv("Eden", null, null, List.of("Java"), null, null, null);

        assertSame(cv, normalizer.normalize(cv, List.of()));
    }

}
