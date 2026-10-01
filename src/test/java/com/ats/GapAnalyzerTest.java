package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.GapAnalysis;
import com.ats.domain.GapAnalysis.NamingMismatch;
import com.ats.domain.GapAnalyzer;
import com.ats.domain.ParsedJob;
import java.util.List;
import org.junit.jupiter.api.Test;

class GapAnalyzerTest {

    private final GapAnalyzer analyzer = new GapAnalyzer();

    private ParsedJob job(String... technologies) {
        return new ParsedJob("Engineer", List.of(), List.of(technologies), List.of(), List.of());
    }

    @Test
    public void GivenTermInResume_WhenAnalyzing_ThenItIsMatched() {
        GapAnalysis gap = analyzer.analyze(job("Java"), "Built services in Java", List.of());

        assertEquals(List.of("Java"), gap.matchedSkills());
        assertTrue(gap.missingKeywords().isEmpty());
        assertTrue(gap.namingMismatches().isEmpty());
    }

    @Test
    public void GivenTermOnlyInKnownSkills_WhenAnalyzing_ThenItIsMatched() {
        GapAnalysis gap = analyzer.analyze(job("Docker"), "Built services", List.of("docker"));

        assertEquals(List.of("Docker"), gap.matchedSkills());
    }

    @Test
    public void GivenTermAbsentEverywhere_WhenAnalyzing_ThenItIsMissing() {
        GapAnalysis gap = analyzer.analyze(job("Terraform"), "Built services in Java", List.of());

        assertEquals(List.of("Terraform"), gap.missingKeywords());
        assertTrue(gap.matchedSkills().isEmpty());
    }

    @Test
    public void GivenJobSaysPostgreSqlAndResumeSaysPostgres_WhenAnalyzing_ThenItIsANamingMismatch() {
        GapAnalysis gap = analyzer.analyze(job("PostgreSQL"), "Tuned Postgres queries", List.of());

        assertEquals(List.of(new NamingMismatch("PostgreSQL", "Postgres")), gap.namingMismatches());
        assertTrue(gap.missingKeywords().isEmpty());
        assertTrue(gap.matchedSkills().isEmpty());
    }

    @Test
    public void GivenAliasOnlyInKnownSkills_WhenAnalyzing_ThenItIsANamingMismatch() {
        GapAnalysis gap = analyzer.analyze(job("Kubernetes"), "Ran clusters", List.of("K8s"));

        assertEquals(List.of(new NamingMismatch("Kubernetes", "K8s")), gap.namingMismatches());
    }

    @Test
    public void GivenCaseSensitiveCatalogTerm_WhenAnalyzing_ThenNoNamingMismatchIsInvented() {
        GapAnalysis gap = analyzer.analyze(job("Spring"), "Used Spring Framework", List.of());

        assertTrue(gap.namingMismatches().isEmpty());
    }

    @Test
    public void GivenUnknownTermNotInResume_WhenAnalyzing_ThenItIsMissing() {
        GapAnalysis gap = analyzer.analyze(job("Salesforce"), "Built services", List.of());

        assertEquals(List.of("Salesforce"), gap.missingKeywords());
    }

    @Test
    public void GivenDomainKeywordsAndTechnologies_WhenAnalyzing_ThenBothAreChecked() {
        ParsedJob parsed = new ParsedJob("Engineer", List.of(), List.of("Java"), List.of(), List.of("microservices", "Java"));

        GapAnalysis gap = analyzer.analyze(parsed, "Java microservices", List.of());

        assertEquals(List.of("Java", "microservices"), gap.matchedSkills());
    }

    @Test
    public void GivenNullResumeText_WhenAnalyzing_ThenEverythingIsMissing() {
        GapAnalysis gap = analyzer.analyze(job("Java"), null, null);

        assertEquals(List.of("Java"), gap.missingKeywords());
    }

}
