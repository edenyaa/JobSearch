package com.ats;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

class JobApiTest extends ApiTestSupport {

    private String jobs(Account account) {
        return "/api/users/" + account.id() + "/jobs";
    }

    private String createJob(Account account, String body) throws Exception {
        return mvc.perform(json(post(jobs(account)), account, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    public void GivenAllFields_WhenCreatingJob_ThenAllFieldsReturned() throws Exception {
        Account account = newAccount();
        String body = "{\"jobNumber\":\"REQ-77\",\"title\":\"Backend Dev\",\"company\":\"Acme\","
                + "\"description\":\"Build APIs\",\"link\":\"https://acme.com/j/77\",\"appliedDate\":\"2026-01-15\"}";

        mvc.perform(json(post(jobs(account)), account, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobNumber").value("REQ-77"))
                .andExpect(jsonPath("$.title").value("Backend Dev"))
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.description").value("Build APIs"))
                .andExpect(jsonPath("$.link").value("https://acme.com/j/77"))
                .andExpect(jsonPath("$.appliedDate").value("2026-01-15"))
                .andExpect(jsonPath("$.hasTailoredResume").value(false));
    }

    @Test
    public void GivenMissingOrBlankJobNumber_WhenCreatingJobs_ThenUniqueGeneratedNumbersAreAssigned() throws Exception {
        Account account = newAccount();

        String first = createJob(account, "{\"description\":\"one\"}");
        String second = createJob(account, "{\"description\":\"two\",\"jobNumber\":\"   \"}");

        String firstNumber = JsonPath.read(first, "$.jobNumber");
        String secondNumber = JsonPath.read(second, "$.jobNumber");
        assertTrue(firstNumber.startsWith("GEN-"));
        assertTrue(secondNumber.startsWith("GEN-"));
        assertNotEquals(firstNumber, secondNumber);
    }

    @Test
    public void GivenNoAppliedDate_WhenCreatingJob_ThenDefaultsToToday() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(jobs(account)), account, "{\"description\":\"d\"}"))
                .andExpect(jsonPath("$.appliedDate").value(LocalDate.now().toString()));
    }

    @Test
    public void GivenMissingOrBlankDescription_WhenCreatingJob_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(jobs(account)), account, "{\"title\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post(jobs(account)), account, "{\"description\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    public void GivenJobsOfTwoUsers_WhenListing_ThenOnlyOwnJobsNewestFirst() throws Exception {
        Account account = newAccount();
        Account other = newAccount();
        createJob(account, "{\"description\":\"old\",\"appliedDate\":\"2025-01-01\"}");
        createJob(account, "{\"description\":\"new\",\"appliedDate\":\"2026-06-01\"}");
        createJob(other, "{\"description\":\"other\"}");

        mvc.perform(get(jobs(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("new"))
                .andExpect(jsonPath("$[1].description").value("old"));
    }

    @Test
    public void GivenExistingJob_WhenGetting_ThenJobReturned() throws Exception {
        Account account = newAccount();
        long id = idOf(createJob(account, "{\"jobNumber\":\"J1\",\"description\":\"d\"}"));

        mvc.perform(get(jobs(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobNumber").value("J1"));
    }

    @Test
    public void GivenExistingJob_WhenUpdating_ThenFieldsAreChanged() throws Exception {
        Account account = newAccount();
        long id = idOf(createJob(account, "{\"jobNumber\":\"J1\",\"description\":\"d\"}"));

        mvc.perform(json(put(jobs(account) + "/" + id), account,
                "{\"jobNumber\":\"J2\",\"description\":\"changed\",\"title\":\"T\",\"appliedDate\":\"2026-02-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobNumber").value("J2"))
                .andExpect(jsonPath("$.description").value("changed"))
                .andExpect(jsonPath("$.title").value("T"))
                .andExpect(jsonPath("$.appliedDate").value("2026-02-02"));
    }

    @Test
    public void GivenUpdateWithoutJobNumberOrDate_WhenUpdatingJob_ThenExistingValuesAreKept() throws Exception {
        Account account = newAccount();
        long id = idOf(createJob(account, "{\"jobNumber\":\"KEEP-1\",\"description\":\"d\",\"appliedDate\":\"2026-03-03\"}"));

        mvc.perform(json(put(jobs(account) + "/" + id), account, "{\"description\":\"edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobNumber").value("KEEP-1"))
                .andExpect(jsonPath("$.appliedDate").value("2026-03-03"));
    }

    @Test
    public void GivenExistingJob_WhenDeleting_ThenItIsGone() throws Exception {
        Account account = newAccount();
        long id = idOf(createJob(account, "{\"description\":\"d\"}"));

        mvc.perform(delete(jobs(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(jobs(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenJobWithoutResume_WhenDownloadingResume_ThenNotFound() throws Exception {
        Account account = newAccount();
        long id = idOf(createJob(account, "{\"description\":\"d\"}"));

        mvc.perform(get(jobs(account) + "/" + id + "/resume").header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenUploadedTailoredResume_WhenDownloading_ThenSameBytesAndTypeReturned() throws Exception {
        Account account = newAccount();
        long id = idOf(createJob(account, "{\"description\":\"d\"}"));
        String url = jobs(account) + "/" + id + "/resume";
        MockMultipartFile file = new MockMultipartFile("file", "acme-cv.pdf", "application/pdf", "TAILORED".getBytes());

        mvc.perform(multipart(HttpMethod.PUT, url).file(file).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTailoredResume").value(true))
                .andExpect(jsonPath("$.tailoredResumeFileName").value("acme-cv.pdf"));

        mvc.perform(get(url).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("acme-cv.pdf")))
                .andExpect(content().bytes("TAILORED".getBytes()));
    }

    @Test
    public void GivenUnknownJobId_WhenUploadingResume_ThenNotFound() throws Exception {
        Account account = newAccount();
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes());

        mvc.perform(multipart(HttpMethod.PUT, jobs(account) + "/999999/resume").file(file)
                .header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenJobOfAnotherUser_WhenAccessingItThroughOwnPath_ThenNotFound() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = idOf(createJob(owner, "{\"description\":\"mine\"}"));

        mvc.perform(get(jobs(other) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(jobs(other) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenJobOfAnotherUser_WhenAccessingItThroughOwnersPath_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = idOf(createJob(owner, "{\"description\":\"mine\"}"));

        mvc.perform(get(jobs(owner) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get(jobs(owner) + "/" + id + "/resume").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

}
