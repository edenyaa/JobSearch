package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.Posting;
import com.ats.domain.PostingFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostingFilterTest {

    private static Posting posting(String title, String location) {
        return new Posting("remotive", "1", title, "Acme", location, "https://x.test", "d");
    }

    private static boolean wanted(String title, String location) {
        return PostingFilter.wanted(posting(title, location), List.of("developer", "engineer"),
                List.of("senior", "head of"), List.of("israel", "remote"));
    }

    @Test
    public void GivenMatchingTitleAndPlace_WhenFiltering_ThenWanted() {
        assertTrue(wanted("Java Developer", "Tel Aviv, Israel"));
        assertTrue(wanted("Backend Engineer", "Remote (Worldwide)"));
    }

    @Test
    public void GivenTitleWithoutWantedWord_WhenFiltering_ThenNotWanted() {
        assertFalse(wanted("Account Executive", "Israel"));
    }

    @Test
    public void GivenExcludedWordInTitle_WhenFiltering_ThenNotWantedButPartOfAnotherWordIsFine() {
        assertFalse(wanted("Senior Developer", "Israel"));
        assertFalse(wanted("Head of Engineering", "Israel"));
        assertTrue(wanted("Seniority Tools Developer", "Israel"));
    }

    @Test
    public void GivenShortTitleTerm_WhenFiltering_ThenOnlyWholeWordsMatch() {
        List<String> titles = List.of("ai", "qa");
        List<String> places = List.of("israel");

        assertTrue(PostingFilter.wanted(posting("AI Engineer", "Israel"), titles, List.of(), places));
        assertTrue(PostingFilter.wanted(posting("QA Automation", "Israel"), titles, List.of(), places));
        assertFalse(PostingFilter.wanted(posting("Email Marketer", "Israel"), titles, List.of(), places));
        assertFalse(PostingFilter.wanted(posting("Maintenance Technician", "Israel"), titles, List.of(), places));
    }

    @Test
    public void GivenInternTerm_WhenFiltering_ThenInternshipsMatchButInternalDoesNot() {
        List<String> titles = List.of("intern");
        List<String> places = List.of("israel");

        assertTrue(PostingFilter.wanted(posting("Software Intern", "Israel"), titles, List.of(), places));
        assertTrue(PostingFilter.wanted(posting("QA Internship", "Israel"), titles, List.of(), places));
        assertFalse(PostingFilter.wanted(posting("Internal Tools Engineer", "Israel"), titles, List.of(), places));
        assertFalse(PostingFilter.wanted(posting("International Sales", "Israel"), titles, List.of(), places));
    }

    @Test
    public void GivenPriorityPlaces_WhenChecking_ThenRehovotIsFoundCaseInsensitively() {
        assertTrue(PostingFilter.inPriorityPlace(posting("Dev", "REHOVOT, Israel"), List.of("rehovot")));
        assertFalse(PostingFilter.inPriorityPlace(posting("Dev", "Tel Aviv"), List.of("rehovot")));
        assertFalse(PostingFilter.inPriorityPlace(posting("Dev", "Tel Aviv"), List.of()));
    }

    @Test
    public void GivenOtherPlace_WhenFiltering_ThenNotWanted() {
        assertFalse(wanted("Java Developer", "Boston, MA"));
    }

    @Test
    public void GivenNoLocationsConfigured_WhenFiltering_ThenAnyPlaceIsAccepted() {
        assertTrue(PostingFilter.wanted(posting("Developer", "Boston"), List.of("developer"), List.of(), List.of()));
    }

    @Test
    public void GivenEscapedHtml_WhenConvertingToText_ThenTagsAreRemovedAndEntitiesDecoded() {
        String text = PostingFilter.text("&lt;p&gt;Build &amp;amp; run APIs&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;/ul&gt;");

        assertEquals("Build & run APIs\n\nJava", text);
    }

    @Test
    public void GivenPlainHtml_WhenConvertingToText_ThenParagraphsStaySeparated() {
        assertEquals("One\n\nTwo", PostingFilter.text("<p>One</p><p>Two</p>"));
        assertEquals("", PostingFilter.text(null));
    }

    @Test
    public void GivenLinkWithAdvertisingParameters_WhenCleaning_ThenOnlyTheMeaningfulPartRemains() {
        assertEquals("https://amazon.jobs/content/en/teams/annapurna-labs?country%5B%5D=IL",
                PostingFilter.cleanUrl("https://amazon.jobs/content/en/teams/annapurna-labs?country%5B%5D=IL&cmpid=SA_GOAG200485C"
                        + "&gad_source=1&gad_campaignid=23946687794&gbraid=0AAA&gclid=Cj0K#search"));
        assertEquals("https://x.com/careers", PostingFilter.cleanUrl("  https://x.com/careers?utm_source=a&fbclid=b#top "));
        assertEquals("https://x.com/jobs?team=qa&city=tlv", PostingFilter.cleanUrl("https://x.com/jobs?team=qa&city=tlv"));
        assertEquals("https://x.com/careers", PostingFilter.cleanUrl("https://x.com/careers"));
    }

    @Test
    public void GivenCountryCode_WhenNaming_ThenItBecomesTheEnglishName() {
        assertEquals("Israel", com.ats.service.JobBoards.countryName("IL"));
        assertEquals("Israel", com.ats.service.JobBoards.countryName("Israel"));
    }

    @Test
    public void GivenCompanyName_WhenGuessingBoards_ThenLegalSuffixIsDropped() {
        assertEquals(List.of("acmesoft", "acme-soft"), PostingFilter.slugGuesses("Acme Soft Ltd."));
        assertEquals("wixcom", PostingFilter.key("Wix.com"));
        assertEquals(List.of("monday"), PostingFilter.slugGuesses("monday.com"));
        assertEquals(List.of("checkpoint", "check-point"), PostingFilter.slugGuesses("Check Point"));
    }

}
