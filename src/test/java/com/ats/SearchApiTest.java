package com.ats;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ats.domain.Posting;
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

    @Test
    public void GivenNewUser_WhenGettingSearch_ThenDefaultsAndStarterCompaniesAreCreated() throws Exception {
        Account account = newAccount();

        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titles", hasItem("developer")))
                .andExpect(jsonPath("$.excludedTitles", hasItem("senior")))
                .andExpect(jsonPath("$.minMatchPercent").value(20))
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
                + "\"locations\":[\"tel aviv\"],\"minMatchPercent\":40,\"daily\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titles", contains("qa")))
                .andExpect(jsonPath("$.locations", contains("tel aviv")))
                .andExpect(jsonPath("$.minMatchPercent").value(40))
                .andExpect(jsonPath("$.daily").value(true));
    }

    @Test
    public void GivenNoTitles_WhenSaving_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(put(base(account)), account,
                "{\"titles\":[],\"excludedTitles\":[],\"locations\":[],\"minMatchPercent\":20,\"daily\":false}"))
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
                new Posting("remotive", "1", "Java Developer", "Acme", "Worldwide", "https://x.test/1",
                        "<p>We need a Java developer with strong Java skills for our team of engineers.</p>"),
                new Posting("remotive", "2", "Senior Java Developer", "Acme", "Worldwide", "https://x.test/2",
                        "<p>We need a Java developer with strong Java skills for our team of engineers.</p>"),
                new Posting("remotive", "3", "Java Developer", "Elsewhere", "USA only", "https://x.test/3",
                        "<p>We need a Java developer with strong Java skills for our team of engineers.</p>"),
                new Posting("remotive", "4", "Java Developer", "Nomatch", "Worldwide", "https://x.test/4",
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
    public void GivenAnotherUsersToken_WhenRunning_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();

        mvc.perform(post(base(owner) + "/run").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

}
