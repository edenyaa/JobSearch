package com.ats;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

class CvAnalysisApiTest extends ApiTestSupport {

    private void uploadCv(Account account, String fileName, String contentType, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, content.getBytes());
        mvc.perform(multipart(HttpMethod.PUT, "/api/users/" + account.id() + "/cv").file(file)
                .header("Authorization", account.bearer()))
                .andExpect(status().isOk());
    }

    private void createSkill(Account account, String body) throws Exception {
        mvc.perform(json(post("/api/users/" + account.id() + "/skills"), account, body))
                .andExpect(status().isCreated());
    }

    private long createJob(Account account, String description) throws Exception {
        String response = mvc.perform(json(post("/api/users/" + account.id() + "/jobs"), account,
                "{\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    @Test
    public void GivenUploadedTxtCv_WhenGettingCvText_ThenTextIsReturned() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "text/plain", "Java developer");

        mvc.perform(get("/api/users/" + account.id() + "/cv/text").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Java developer"));
    }

    @Test
    public void GivenNoCv_WhenGettingCvText_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(get("/api/users/" + account.id() + "/cv/text").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenCvWithUnsupportedFormat_WhenGettingCvText_ThenBadRequest() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.exe", "application/octet-stream", "binary");

        mvc.perform(get("/api/users/" + account.id() + "/cv/text").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenAnotherUsersToken_WhenGettingCvText_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();

        mvc.perform(get("/api/users/" + owner.id() + "/cv/text").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void GivenSkillsCvAndJob_WhenMatching_ThenMatchedAndMissingTermsAreReported() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "text/plain", "Backend engineer using Java and Spring");
        createSkill(account, "{\"name\":\"Java\",\"keywords\":[\"Spring\",\"Kubernetes\"]}");
        createSkill(account, "{\"name\":\"Python\"}");
        long jobId = createJob(account, "We want Java, Spring and Kubernetes experience");

        mvc.perform(get("/api/users/" + account.id() + "/jobs/" + jobId + "/match")
                .header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveragePercent").value(66))
                .andExpect(jsonPath("$.matchedTerms", containsInAnyOrder("Java", "Spring")))
                .andExpect(jsonPath("$.missingTerms", contains("Kubernetes")));
    }

    @Test
    public void GivenNoSkills_WhenMatching_ThenZeroCoverageAndNoTerms() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "text/plain", "Some CV");
        long jobId = createJob(account, "Java developer");

        mvc.perform(get("/api/users/" + account.id() + "/jobs/" + jobId + "/match")
                .header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveragePercent").value(0))
                .andExpect(jsonPath("$.matchedTerms", hasSize(0)))
                .andExpect(jsonPath("$.missingTerms", hasSize(0)));
    }

    @Test
    public void GivenNoCv_WhenMatching_ThenBadRequest() throws Exception {
        Account account = newAccount();
        long jobId = createJob(account, "Java developer");

        mvc.perform(get("/api/users/" + account.id() + "/jobs/" + jobId + "/match")
                .header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenUnknownJob_WhenMatching_ThenNotFound() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "text/plain", "cv");

        mvc.perform(get("/api/users/" + account.id() + "/jobs/999999/match").header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenAnotherUsersToken_WhenMatching_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long jobId = createJob(owner, "Java developer");

        mvc.perform(get("/api/users/" + owner.id() + "/jobs/" + jobId + "/match")
                .header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

}
