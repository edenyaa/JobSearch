package com.ats;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class JobStatusApiTest extends ApiTestSupport {

    private String jobs(Account account) {
        return "/api/users/" + account.id() + "/jobs";
    }

    private long createJob(Account account, String body) throws Exception {
        String response = mvc.perform(json(post(jobs(account)), account, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    @Test
    public void GivenNoStatus_WhenCreatingJob_ThenStatusDefaultsToApplied() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(jobs(account)), account, "{\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    public void GivenStatusAndNotes_WhenCreatingJob_ThenTheyAreStored() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(jobs(account)), account,
                "{\"description\":\"d\",\"status\":\"SAVED\",\"notes\":\"call recruiter Monday\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.notes").value("call recruiter Monday"));
    }

    @Test
    public void GivenUnknownStatusValue_WhenCreatingJob_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(jobs(account)), account, "{\"description\":\"d\",\"status\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenExistingJob_WhenChangingStatus_ThenStatusIsUpdated() throws Exception {
        Account account = newAccount();
        long id = createJob(account, "{\"description\":\"d\"}");

        mvc.perform(json(put(jobs(account) + "/" + id + "/status"), account, "{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"));

        mvc.perform(get(jobs(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.status").value("INTERVIEW"));
    }

    @Test
    public void GivenMissingOrInvalidStatus_WhenChangingStatus_ThenBadRequest() throws Exception {
        Account account = newAccount();
        long id = createJob(account, "{\"description\":\"d\"}");
        String url = jobs(account) + "/" + id + "/status";

        mvc.perform(json(put(url), account, "{}")).andExpect(status().isBadRequest());
        mvc.perform(json(put(url), account, "{\"status\":\"NOPE\"}")).andExpect(status().isBadRequest());
    }

    @Test
    public void GivenUnknownJob_WhenChangingStatus_ThenNotFound() throws Exception {
        Account account = newAccount();

        mvc.perform(json(put(jobs(account) + "/999999/status"), account, "{\"status\":\"OFFER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenJobOfAnotherUser_WhenChangingItsStatus_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = createJob(owner, "{\"description\":\"d\"}");

        mvc.perform(json(put(jobs(owner) + "/" + id + "/status"), other, "{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void GivenJobsWithDifferentStatuses_WhenFilteringByStatus_ThenOnlyMatchingJobsReturned() throws Exception {
        Account account = newAccount();
        createJob(account, "{\"description\":\"a\",\"status\":\"INTERVIEW\"}");
        createJob(account, "{\"description\":\"b\",\"status\":\"REJECTED\"}");
        createJob(account, "{\"description\":\"c\",\"status\":\"INTERVIEW\"}");

        mvc.perform(get(jobs(account)).param("status", "INTERVIEW").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", everyItem(is("INTERVIEW"))));
    }

    @Test
    public void GivenNoFilter_WhenListingJobs_ThenAllStatusesReturned() throws Exception {
        Account account = newAccount();
        createJob(account, "{\"description\":\"a\",\"status\":\"OFFER\"}");
        createJob(account, "{\"description\":\"b\",\"status\":\"WITHDRAWN\"}");

        mvc.perform(get(jobs(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    public void GivenUpdateWithoutStatusOrNotes_WhenUpdatingJob_ThenExistingValuesAreKept() throws Exception {
        Account account = newAccount();
        long id = createJob(account, "{\"description\":\"d\",\"status\":\"INTERVIEW\",\"notes\":\"keep me\"}");

        mvc.perform(json(put(jobs(account) + "/" + id), account, "{\"description\":\"edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.notes").value("keep me"));
    }

}
