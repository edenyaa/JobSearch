package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.Posting;
import com.ats.service.JobBoards;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JobPostingMarkupTest {

    private final JobBoards boards = new JobBoards(JsonMapper.builder().build());

    private static String page(String json) {
        return "<html><head><script type=\"application/ld+json\">" + json + "</script></head></html>";
    }

    @Test
    public void GivenSingleJobPosting_WhenParsing_ThenFieldsAreRead() {
        List<Posting> found = boards.parseJobPostings(page("{\"@context\":\"https://schema.org\",\"@type\":\"JobPosting\","
                + "\"title\":\"Java Developer\",\"identifier\":{\"value\":\"J-7\"},\"url\":\"https://acme.com/jobs/7\","
                + "\"description\":\"<p>Build APIs</p>\",\"hiringOrganization\":{\"name\":\"Acme Ltd\"},"
                + "\"jobLocation\":{\"address\":{\"addressLocality\":\"Tel Aviv\",\"addressCountry\":\"IL\"}}}"),
                "https://acme.com/careers", "");

        assertEquals(1, found.size());
        Posting p = found.get(0);
        assertEquals("Java Developer", p.title());
        assertEquals("Acme Ltd", p.company());
        assertEquals("Tel Aviv IL", p.location());
        assertEquals("https://acme.com/jobs/7", p.link());
        assertEquals("page-J-7", p.jobNumber());
    }

    @Test
    public void GivenGraphWithSeveralPostingsAndRemoteJob_WhenParsing_ThenAllAreFound() {
        List<Posting> found = boards.parseJobPostings(page("{\"@graph\":[{\"@type\":\"Organization\",\"name\":\"X\"},"
                + "{\"@type\":\"JobPosting\",\"title\":\"A\",\"url\":\"https://x.com/a\",\"jobLocationType\":\"TELECOMMUTE\"},"
                + "{\"@type\":[\"JobPosting\"],\"title\":\"B\",\"url\":\"https://x.com/b\"}]}"),
                "https://x.com/careers", "Xco");

        assertEquals(List.of("A", "B"), found.stream().map(Posting::title).toList());
        assertEquals("Remote", found.get(0).location());
        assertEquals("Xco", found.get(0).company());
    }

    @Test
    public void GivenItemListOfJobs_WhenParsing_ThenListedJobsAreFound() {
        List<Posting> found = boards.parseJobPostings(page("{\"@type\":\"ItemList\",\"itemListElement\":["
                + "{\"@type\":\"ListItem\",\"item\":{\"@type\":\"JobPosting\",\"title\":\"QA\",\"url\":\"https://x.com/qa\"}}]}"),
                "https://x.com/careers", "Xco");

        assertEquals(1, found.size());
        assertEquals("QA", found.get(0).title());
    }

    @Test
    public void GivenBrokenOrMissingJsonLd_WhenParsing_ThenNothingIsFoundAndNothingFails() {
        assertTrue(boards.parseJobPostings(page("{not json"), "https://x.com", "X").isEmpty());
        assertTrue(boards.parseJobPostings("<html>no data</html>", "https://x.com", "X").isEmpty());
        assertTrue(boards.parseJobPostings(page("{\"@type\":\"WebSite\"}"), "https://x.com", "X").isEmpty());
    }

}
