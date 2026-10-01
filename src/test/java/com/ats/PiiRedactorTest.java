package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.PiiRedactor;
import com.ats.domain.PiiRedactor.Redaction;
import org.junit.jupiter.api.Test;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    public void GivenEmail_WhenRedacting_ThenItIsReplacedByPlaceholder() {
        Redaction redaction = redactor.redact("Contact me at eden@example.com today", null);

        assertEquals("Contact me at [[EMAIL_1]] today", redaction.text());
        assertEquals("eden@example.com", redaction.replacements().get("[[EMAIL_1]]"));
    }

    @Test
    public void GivenTwoEmails_WhenRedacting_ThenEachGetsItsOwnPlaceholder() {
        Redaction redaction = redactor.redact("a@x.com and b@y.org", null);

        assertEquals("[[EMAIL_1]] and [[EMAIL_2]]", redaction.text());
    }

    @Test
    public void GivenInternationalAndLocalPhones_WhenRedacting_ThenBothAreReplaced() {
        Redaction international = redactor.redact("Phone: +972 54 123 4567", null);
        Redaction local = redactor.redact("Phone: (054) 123-4567", null);

        assertEquals("Phone: [[PHONE_1]]", international.text());
        assertEquals("Phone: [[PHONE_1]]", local.text());
    }

    @Test
    public void GivenYearRanges_WhenRedacting_ThenTheyAreNotTreatedAsPhones() {
        String text = "2019 - 2023 Backend engineer, 2015-2018 Student";

        Redaction redaction = redactor.redact(text, null);

        assertEquals(text, redaction.text());
        assertTrue(redaction.replacements().isEmpty());
    }

    @Test
    public void GivenLinks_WhenRedacting_ThenTheyAreReplacedWithoutTrailingPunctuation() {
        Redaction redaction = redactor.redact("See https://linkedin.com/in/eden, and github.com/eden.", null);

        assertEquals("See [[LINK_1]], and [[LINK_2]].", redaction.text());
        assertEquals("https://linkedin.com/in/eden", redaction.replacements().get("[[LINK_1]]"));
        assertEquals("github.com/eden", redaction.replacements().get("[[LINK_2]]"));
    }

    @Test
    public void GivenKnownName_WhenRedacting_ThenNameIsReplacedCaseInsensitively() {
        Redaction redaction = redactor.redact("Eden Yaakobi\nEDEN YAAKOBI is a developer", "Eden Yaakobi");

        assertEquals("[[NAME]]\n[[NAME]] is a developer", redaction.text());
        assertEquals("Eden Yaakobi", redaction.replacements().get("[[NAME]]"));
    }

    @Test
    public void GivenNoKnownNameOrBlankName_WhenRedacting_ThenTextIsUnchanged() {
        assertEquals("Eden is here", redactor.redact("Eden is here", null).text());
        assertEquals("Eden is here", redactor.redact("Eden is here", "  ").text());
    }

    @Test
    public void GivenNullText_WhenRedacting_ThenEmptyTextIsReturned() {
        Redaction redaction = redactor.redact(null, "Eden");

        assertEquals("", redaction.text());
        assertTrue(redaction.replacements().isEmpty());
    }

    @Test
    public void GivenRedactedText_WhenRestoring_ThenOriginalDetailsComeBack() {
        Redaction redaction = redactor.redact("Eden Yaakobi eden@example.com +972 54 123 4567", "Eden Yaakobi");

        String restored = redaction.restore(redaction.text());

        assertEquals("Eden Yaakobi eden@example.com +972 54 123 4567", restored);
    }

    @Test
    public void GivenTextWithPlaceholderInsideSentence_WhenRestoring_ThenItIsReplaced() {
        Redaction redaction = redactor.redact("mail me: eden@example.com", null);

        assertEquals("Email eden@example.com now", redaction.restore("Email [[EMAIL_1]] now"));
    }

    @Test
    public void GivenNullAndUnknownPlaceholders_WhenRestoring_ThenNullStaysNullAndUnknownStaysAsIs() {
        Redaction redaction = redactor.redact("a@x.com", null);

        assertNull(redaction.restore(null));
        assertEquals("[[EMAIL_9]]", redaction.restore("[[EMAIL_9]]"));
    }

    @Test
    public void GivenTextWithoutPersonalDetails_WhenRedacting_ThenNothingIsReplaced() {
        Redaction redaction = redactor.redact("Java and Spring Boot developer", "Eden");

        assertEquals("Java and Spring Boot developer", redaction.text());
        assertTrue(redaction.replacements().isEmpty());
    }

}
