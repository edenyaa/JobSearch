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
    public void GivenCompanyName_WhenGuessingBoards_ThenLegalSuffixIsDropped() {
        assertEquals(List.of("acmesoft", "acme-soft"), PostingFilter.slugGuesses("Acme Soft Ltd."));
        assertEquals("wixcom", PostingFilter.key("Wix.com"));
    }

}
