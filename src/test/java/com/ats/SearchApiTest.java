package com.ats;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ats.domain.Posting;
import com.ats.service.FitJudge;
import com.ats.service.FitJudge.Fit;
import com.ats.service.JobBoards;
import com.ats.service.JobBoards.Board;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SearchApiTest extends ApiTestSupport {

    @MockitoBean
    JobBoards boards;

    /** Without a stub the AI gives no rating, as when it is unavailable. */
    @MockitoBean
    FitJudge judge;

    @Autowired
    com.ats.service.JobSearchService search;

    private String base(Account a) {
        return "/api/users/" + a.id() + "/search";
    }

    private void uploadCv(Account account, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.txt", "text/plain", content.getBytes());
        mvc.perform(multipart(HttpMethod.PUT, "/api/users/" + account.id() + "/cv").file(file)
                .header("Authorization", account.bearer())).andExpect(status().isOk());
    }

    private void createSkill(Account account, String name) throws Exception {
        mvc.perform(json(post("/api/users/" + account.id() + "/skills"), account, "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
    }

    private void waitUntilFinished(Account account) throws Exception {
        for (int i = 0; i < 100; i++) {
            String body = mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                    .andReturn().getResponse().getContentAsString();
            if (!(Boolean) JsonPath.read(body, "$.running") && JsonPath.read(body, "$.lastRunAt") != null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The search did not finish");
    }

    private void waitUntilImported(Account account) throws Exception {
        for (int i = 0; i < 200; i++) {
            String body = mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                    .andReturn().getResponse().getContentAsString();
            Object status = JsonPath.read(body, "$.importStatus");
            if (!(Boolean) JsonPath.read(body, "$.importing") && status != null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The import did not finish");
    }

    @Test
    public void GivenNewUser_WhenGettingSearch_ThenDefaultsAndStarterCompaniesAreCreated() throws Exception {
        Account account = newAccount();

        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titles", hasItem("developer")))
                .andExpect(jsonPath("$.excludedTitles", hasItems("senior", "lead", "director")))
                .andExpect(jsonPath("$.excludedTitles", not(hasItem("intern"))))
                .andExpect(jsonPath("$.minMatchPercent").value(20))
                .andExpect(jsonPath("$.minFitPercent").value(40))
                .andExpect(jsonPath("$.priorityLocations", hasItem("rehovot")))
                .andExpect(jsonPath("$.titles", hasItems("qa", "junior", "student", "intern", "trainee", "prompt")))
                .andExpect(jsonPath("$.locations", not(hasItem("remote"))))
                .andExpect(jsonPath("$.daily").value(false))
                .andExpect(jsonPath("$.running").value(false));
        mvc.perform(get(base(account) + "/companies").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("jfrog")));
    }

    @Test
    public void GivenNewSettings_WhenSaving_ThenTheyAreStoredAndBlankEntriesDropped() throws Exception {
        Account account = newAccount();

        mvc.perform(json(put(base(account)), account, "{\"titles\":[\"qa\",\" \",\"qa\"],\"excludedTitles\":[],"
                + "\"locations\":[\"tel aviv\"],\"priorityLocations\":[\"rehovot\"],\"minMatchPercent\":40,"
                + "\"minFitPercent\":55,\"daily\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titles", contains("qa")))
                .andExpect(jsonPath("$.locations", contains("tel aviv")))
                .andExpect(jsonPath("$.minMatchPercent").value(40))
                .andExpect(jsonPath("$.minFitPercent").value(55))
                .andExpect(jsonPath("$.priorityLocations", contains("rehovot")))
                .andExpect(jsonPath("$.daily").value(true));
    }

    @Test
    public void GivenNoTitles_WhenSaving_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(put(base(account)), account,
                "{\"titles\":[],\"excludedTitles\":[],\"locations\":[],\"priorityLocations\":[],"
                + "\"minMatchPercent\":20,\"minFitPercent\":40,\"daily\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenCareerPageLink_WhenAddingCompany_ThenItIsSearchedAndShownOnCompaniesPage() throws Exception {
        Account account = newAccount();
        when(boards.exists(any())).thenReturn(true);

        mvc.perform(json(post(base(account) + "/companies"), account,
                "{\"company\":\"https://job-boards.greenhouse.io/wixcom/jobs/123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("wixcom"))
                .andExpect(jsonPath("$.board").value("greenhouse"));
        mvc.perform(get("/api/users/" + account.id() + "/companies").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$[*].name", hasItem("Wixcom")));
    }

    @Test
    public void GivenCompanyName_WhenAddingCompany_ThenItsBoardIsFoundAutomatically() throws Exception {
        Account account = newAccount();
        when(boards.findBoard("Globex")).thenReturn(Optional.of(new Board("lever", "globex")));
        when(boards.exists(any())).thenReturn(true);

        mvc.perform(json(post(base(account) + "/companies"), account, "{\"company\":\"Globex\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.board").value("lever"))
                .andExpect(jsonPath("$.name").value("Globex"));
    }

    @Test
    public void GivenAshbyLink_WhenAddingCompany_ThenAshbyBoardIsStored() throws Exception {
        Account account = newAccount();
        when(boards.exists(any())).thenReturn(true);

        mvc.perform(json(post(base(account) + "/companies"), account, "{\"company\":\"https://jobs.ashbyhq.com/notion\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.board").value("ashby"))
                .andExpect(jsonPath("$.slug").value("notion"));
    }

    @Test
    public void GivenCompanyCareerSiteLink_WhenAddingCompany_ThenItIsStoredAsPageNamedAfterItsHost() throws Exception {
        Account account = newAccount();
        when(boards.exists(any())).thenReturn(true);

        mvc.perform(json(post(base(account) + "/companies"), account,
                "{\"company\":\"https://www.example-corp.com/careers\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.board").value("page"))
                .andExpect(jsonPath("$.name").value("Example-corp"));
    }

    @Test
    public void GivenCareerSiteWithoutJobData_WhenAddingCompany_ThenBadRequestExplains() throws Exception {
        Account account = newAccount();
        when(boards.exists(any())).thenReturn(false);

        mvc.perform(json(post(base(account) + "/companies"), account,
                "{\"company\":\"https://www.example-corp.com/careers\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenNoJsearchKey_WhenGettingSearch_ThenAggregatorIsReportedOff() throws Exception {
        Account account = newAccount();

        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.aggregatorEnabled").value(false));
    }

    @Test
    public void GivenNameWithoutCareerPage_WhenAddingCompany_ThenBadRequest() throws Exception {
        Account account = newAccount();
        when(boards.findBoard(anyString())).thenReturn(Optional.empty());

        mvc.perform(json(post(base(account) + "/companies"), account, "{\"company\":\"Nowhere\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenCompanyAlreadySearched_WhenAddingAgain_ThenConflict() throws Exception {
        Account account = newAccount();
        when(boards.exists(any())).thenReturn(true);
        String body = "{\"company\":\"https://jobs.lever.co/acme\"}";
        mvc.perform(json(post(base(account) + "/companies"), account, body)).andExpect(status().isCreated());

        mvc.perform(json(post(base(account) + "/companies"), account, body)).andExpect(status().isConflict());
    }

    @Test
    public void GivenSearchedCompany_WhenRemoving_ThenItIsGone() throws Exception {
        Account account = newAccount();
        when(boards.exists(any())).thenReturn(true);
        String created = mvc.perform(json(post(base(account) + "/companies"), account,
                "{\"company\":\"https://jobs.lever.co/acme\"}")).andReturn().getResponse().getContentAsString();

        mvc.perform(delete(base(account) + "/companies/" + idOf(created)).header("Authorization", account.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get(base(account) + "/companies").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$[*].slug", not(hasItem("acme"))));
    }

    @Test
    public void GivenNoCv_WhenRunning_ThenSummaryExplainsAndNothingIsSaved() throws Exception {
        Account account = newAccount();

        mvc.perform(post(base(account) + "/run").header("Authorization", account.bearer()))
                .andExpect(status().isAccepted());
        waitUntilFinished(account);

        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.lastRunSummary", containsString("Upload a main CV")));
    }

    @Test
    public void GivenCvSkillsAndOpenings_WhenRunning_ThenRelevantNewJobsAreSavedWithCompanies() throws Exception {
        Account account = newAccount();
        uploadCv(account, "Java developer with Spring experience");
        createSkill(account, "Java");
        when(boards.remotive(anyString())).thenReturn(List.of(
                new Posting("remotive", "1", "Java Developer", "Acme", "Tel Aviv, Israel", "https://x.test/1",
                        "<p>We need a Java developer with strong Java skills for our team of engineers.</p>"),
                new Posting("remotive", "2", "Senior Java Developer", "Acme", "Tel Aviv, Israel", "https://x.test/2",
                        "<p>We need a Java developer with strong Java skills for our team of engineers.</p>"),
                new Posting("remotive", "3", "Java Developer", "Elsewhere", "USA only", "https://x.test/3",
                        "<p>We need a Java developer with strong Java skills for our team of engineers.</p>"),
                new Posting("remotive", "4", "Java Developer", "Nomatch", "Tel Aviv, Israel", "https://x.test/4",
                        "<p>A developer role about gardening and cooking for a restaurant company in town.</p>")));
        when(boards.arbeitnow()).thenReturn(List.of());
        when(boards.openings(any(), anyString())).thenReturn(List.of());
        when(boards.findBoard(anyString())).thenReturn(Optional.empty());

        mvc.perform(post(base(account) + "/run").header("Authorization", account.bearer()))
                .andExpect(status().isAccepted());
        waitUntilFinished(account);

        mvc.perform(get("/api/users/" + account.id() + "/jobs").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Java Developer"))
                .andExpect(jsonPath("$[0].company").value("Acme"))
                .andExpect(jsonPath("$[0].status").value("SAVED"))
                .andExpect(jsonPath("$[0].link").value("https://x.test/1"))
                .andExpect(jsonPath("$[0].notes", containsString("Keyword match: 100%")));
        mvc.perform(get("/api/users/" + account.id() + "/companies").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$[*].name", hasItem("Acme")));

        // a second run finds the same openings and saves nothing new
        mvc.perform(post(base(account) + "/run").header("Authorization", account.bearer()));
        Thread.sleep(300);
        waitUntilFinished(account);
        mvc.perform(get("/api/users/" + account.id() + "/jobs").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    public void GivenAiRatingsAndRehovotJob_WhenRunning_ThenFitAndPriorityAreStoredAndWeakFitsDropped() throws Exception {
        Account account = newAccount();
        uploadCv(account, "Java developer with Spring experience");
        createSkill(account, "Java");
        String text = "<p>We need a Java developer with strong Java skills for our team of engineers.</p>";
        when(boards.remotive(anyString())).thenReturn(List.of(
                new Posting("remotive", "1", "Junior Java Developer", "Alpha", "Tel Aviv, Israel", "https://x.test/1", text),
                new Posting("remotive", "2", "Java Developer", "Beta", "Rehovot, Israel", "https://x.test/2", text),
                new Posting("remotive", "3", "Java Developer", "Weak", "Haifa, Israel", "https://x.test/3", text)));
        when(boards.arbeitnow()).thenReturn(List.of());
        when(boards.openings(any(), anyString())).thenReturn(List.of());
        when(boards.findBoard(anyString())).thenReturn(Optional.empty());
        when(judge.judge(anyString(), any(), anyString(), any())).thenAnswer(call -> {
            Posting p = call.getArgument(1);
            return Optional.of("Weak".equals(p.company()) ? new Fit(20, "Different field") : new Fit(80, "Fits well"));
        });

        mvc.perform(post(base(account) + "/run").header("Authorization", account.bearer()));
        waitUntilFinished(account);

        String jobs = "/api/users/" + account.id() + "/jobs";
        mvc.perform(get(jobs).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.company=='Beta')].fitScore", contains(90)))
                .andExpect(jsonPath("$[?(@.company=='Beta')].priorityPlace", contains(true)))
                .andExpect(jsonPath("$[?(@.company=='Beta')].location", contains("Rehovot, Israel")))
                .andExpect(jsonPath("$[?(@.company=='Alpha')].fitScore", contains(80)))
                .andExpect(jsonPath("$[?(@.company=='Alpha')].priorityPlace", contains(false)))
                .andExpect(jsonPath("$[?(@.company=='Alpha')].fitNote", contains("Fits well")))
                .andExpect(jsonPath("$[?(@.company=='Weak')]", hasSize(0)));
        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.lastRunSummary", containsString("1 dropped as a weak fit")));
    }

    @Test
    public void GivenAiLikesJobThatMentionsNoListedSkill_WhenRunning_ThenItIsSavedAndSummaryExplainsTheRun() throws Exception {
        Account account = newAccount();
        uploadCv(account, "Java developer with Spring experience");
        createSkill(account, "Java");
        String gardening = "<p>We are hiring a junior tester to check our mobile app before every release.</p>";
        when(boards.remotive(anyString())).thenReturn(List.of(
                new Posting("remotive", "1", "QA Tester", "Beta", "Rehovot, Israel", "https://x.test/1", gardening),
                new Posting("remotive", "2", "QA Tester", "Faraway", "Boston, MA", "https://x.test/2", gardening)),
                List.of());
        when(boards.arbeitnow()).thenReturn(List.of());
        when(boards.openings(any(), anyString())).thenReturn(List.of());
        when(boards.findBoard(anyString())).thenReturn(Optional.empty());
        when(judge.judge(anyString(), any(), anyString(), any())).thenReturn(Optional.of(new Fit(75, "Junior QA role")));

        mvc.perform(post(base(account) + "/run").header("Authorization", account.bearer()));
        waitUntilFinished(account);

        mvc.perform(get("/api/users/" + account.id() + "/jobs").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].company").value("Beta"));
        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.lastRunSummary", containsString("Checked 2 openings")))
                .andExpect(jsonPath("$.lastRunSummary", containsString("1 fit your titles and places")))
                .andExpect(jsonPath("$.lastRunSummary", containsString("1 rated by the AI")));
    }

    @Test
    public void GivenAiUnavailable_WhenRunning_ThenSummaryTellsSoAndCountsMissingSkills() throws Exception {
        Account account = newAccount();
        uploadCv(account, "Java developer with Spring experience");
        createSkill(account, "Java");
        when(boards.remotive(anyString())).thenReturn(List.of(new Posting("remotive", "1", "QA Tester", "Beta",
                "Rehovot, Israel", "https://x.test/1", "<p>Check our mobile app before every release of the product.</p>")));
        when(boards.arbeitnow()).thenReturn(List.of());
        when(boards.openings(any(), anyString())).thenReturn(List.of());
        when(boards.findBoard(anyString())).thenReturn(Optional.empty());

        mvc.perform(post(base(account) + "/run").header("Authorization", account.bearer()));
        waitUntilFinished(account);

        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.lastRunSummary", containsString("1 mention too few of your skills")))
                .andExpect(jsonPath("$.lastRunSummary", containsString("The AI could not rate jobs")));
    }

    @Test
    public void GivenListOfCompanyNames_WhenImporting_ThenCompaniesWithCareerPagesAreAdded() throws Exception {
        Account account = newAccount();
        when(boards.findBoard("Alphaco")).thenReturn(Optional.of(new Board("greenhouse", "alphaco")));
        when(boards.findBoard("Betaco")).thenReturn(Optional.of(new Board("lever", "betaco")));
        when(boards.findBoard("Nowhere Ltd")).thenReturn(Optional.empty());

        mvc.perform(json(post(base(account) + "/companies/import"), account,
                "{\"names\":[\"Alphaco\",\" \",\"Betaco\",\"Nowhere Ltd\",\"Alphaco\"]}"))
                .andExpect(status().isAccepted());
        waitUntilImported(account);

        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.importing").value(false))
                .andExpect(jsonPath("$.importStatus", containsString("added 2 career pages")))
                .andExpect(jsonPath("$.importStatus", containsString("1 have no Greenhouse, Lever or Ashby page")));
        mvc.perform(get(base(account) + "/companies").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$[*].slug", hasItems("alphaco", "betaco")));
    }

    @Test
    public void GivenNoNames_WhenImporting_ThenTheBuiltInIsraeliListIsLookedUp() throws Exception {
        Account account = newAccount();
        when(boards.findBoard("Wix")).thenReturn(Optional.of(new Board("greenhouse", "wix")));

        mvc.perform(post(base(account) + "/companies/import").header("Authorization", account.bearer()))
                .andExpect(status().isAccepted());
        waitUntilImported(account);

        mvc.perform(get(base(account) + "/companies").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$[*].slug", hasItem("wix")));
    }

    @Test
    public void GivenNoAiRating_WhenRunning_ThenFitComesFromSkillMatchWithEntryLevelAndRehovotBonus() throws Exception {
        Account account = newAccount();
        uploadCv(account, "Java developer with Spring experience");
        createSkill(account, "Java");
        String text = "<p>We need a Java developer with strong Java skills for our team of engineers.</p>";
        when(boards.remotive(anyString())).thenReturn(List.of(
                new Posting("remotive", "1", "Junior Java Developer", "Beta", "Rehovot, Israel", "https://x.test/1", text)));
        when(boards.arbeitnow()).thenReturn(List.of());
        when(boards.openings(any(), anyString())).thenReturn(List.of());
        when(boards.findBoard(anyString())).thenReturn(Optional.empty());

        mvc.perform(post(base(account) + "/run").header("Authorization", account.bearer()));
        waitUntilFinished(account);

        // 100% skill match: 80 + 10 for a junior title + 10 for Rehovot
        mvc.perform(get("/api/users/" + account.id() + "/jobs").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$[0].fitScore").value(100))
                .andExpect(jsonPath("$[0].fitNote", containsString("Not rated by the AI")));
    }

    @Test
    public void GivenChangedSettings_WhenResetting_ThenRecommendedOnesAreBack() throws Exception {
        Account account = newAccount();
        mvc.perform(json(put(base(account)), account, "{\"titles\":[\"x\"],\"excludedTitles\":[],"
                + "\"locations\":[],\"priorityLocations\":[],\"minMatchPercent\":90,\"minFitPercent\":90,\"daily\":true}"))
                .andExpect(status().isOk());

        mvc.perform(post(base(account) + "/reset").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titles", hasItem("qa")))
                .andExpect(jsonPath("$.priorityLocations", hasItem("rehovot")))
                .andExpect(jsonPath("$.minMatchPercent").value(20))
                .andExpect(jsonPath("$.daily").value(true));
    }

    @Test
    public void GivenAnotherUsersToken_WhenRunning_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();

        mvc.perform(post(base(owner) + "/run").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

}
