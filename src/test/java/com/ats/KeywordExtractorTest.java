package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.Analysis.KeywordGap;
import com.ats.domain.KeywordExtractor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeywordExtractorTest {

    private final KeywordExtractor extractor = new KeywordExtractor();

    private List<String> terms(List<KeywordGap> gaps) {
        return gaps.stream().map(KeywordGap::term).toList();
    }

    @Test
    public void GivenPhraseRepeatedInPosting_WhenExtracting_ThenItIsKeptAsOneKeywordWithItsMentions() {
        String job = "Our payment systems scale. Payment systems matter. We process payments.";

        List<KeywordGap> gaps = extractor.gaps(job, "", Set.of());

        assertEquals(List.of("payment systems"), terms(gaps));
        assertEquals(2, gaps.get(0).mentions());
    }

    @Test
    public void GivenKeywordPresentInCv_WhenExtracting_ThenItIsMarkedAsInCvIncludingPluralVariants() {
        String job = "Payment systems here. Payment systems there.";

        assertTrue(extractor.gaps(job, "I built payment systems", Set.of()).get(0).inCv());
        assertTrue(extractor.gaps(job, "I built payment system integrations", Set.of()).get(0).inCv());
        assertTrue(extractor.gaps(job, "PAYMENT SYSTEMS", Set.of()).get(0).inCv());
    }

    @Test
    public void GivenKeywordMissingFromCv_WhenExtracting_ThenItIsMarkedAsMissing() {
        String job = "Payment systems here. Payment systems there.";

        assertFalse(extractor.gaps(job, "I bake bread", Set.of()).get(0).inCv());
    }

    @Test
    public void GivenWordsMentionedOnlyOnce_WhenExtracting_ThenTheyAreIgnored() {
        assertTrue(extractor.gaps("We need a fintech engineer who knows ledgers", "", Set.of()).isEmpty());
    }

    @Test
    public void GivenCatalogSkillsRepeated_WhenExtracting_ThenTheyAreNotListedAgain() {
        assertTrue(extractor.gaps("Docker Docker Docker Java Java", "", Set.of()).isEmpty());
    }

    @Test
    public void GivenBoilerplateWords_WhenExtracting_ThenTheyAreIgnored() {
        assertTrue(extractor.gaps("experience experience team team skills skills", "", Set.of()).isEmpty());
    }

    @Test
    public void GivenNumbersRepeated_WhenExtracting_ThenTheyAreIgnored() {
        assertTrue(extractor.gaps("2024 2024 2024", "", Set.of()).isEmpty());
    }

    @Test
    public void GivenExcludedTerms_WhenExtracting_ThenTheyAreSkipped() {
        List<KeywordGap> gaps = extractor.gaps("Salesforce Salesforce admin admin", "", Set.of("salesforce"));

        assertEquals(List.of("admin"), terms(gaps));
    }

    @Test
    public void GivenManyRepeatedWords_WhenExtracting_ThenResultIsCapped() {
        StringBuilder job = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            String word = "term" + (char) ('a' + (i % 26)) + (char) ('a' + (i / 26));
            job.append(word).append(" and ").append(word).append(". ");
        }

        assertTrue(extractor.gaps(job.toString(), "", Set.of()).size() <= 12);
    }

    @Test
    public void GivenNullInputs_WhenExtracting_ThenNothingIsReturned() {
        assertTrue(extractor.gaps(null, null, Set.of()).isEmpty());
    }

    @Test
    public void GivenMoreFrequentKeyword_WhenExtracting_ThenItComesFirst() {
        String job = "Ledger work. Ledger design. Ledger audit. Reconciliation here. Reconciliation there.";

        List<String> terms = terms(extractor.gaps(job, "", Set.of()));

        assertEquals("ledger", terms.get(0));
        assertTrue(terms.contains("reconciliation"));
    }

}
