package com.ats;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class CompanyApiTest extends ApiTestSupport {

    private String companies(Account account) {
        return "/api/users/" + account.id() + "/companies";
    }

    private long createCompany(Account account, String body) throws Exception {
        String response = mvc.perform(json(post(companies(account)), account, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    private void createJob(Account account, String company, String title) throws Exception {
        mvc.perform(json(post("/api/users/" + account.id() + "/jobs"), account,
                "{\"company\":\"" + company + "\",\"title\":\"" + title + "\",\"description\":\"d\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    public void GivenCompanyWithSeveralContacts_WhenCreatingAndGetting_ThenAllReturnedInOrder() throws Exception {
        Account account = newAccount();

        long id = createCompany(account, "{\"name\":\"Acme\",\"contacts\":["
                + "{\"name\":\"Dana\",\"whereKnown\":\"TAU\",\"linkedin\":\"https://linkedin.com/in/dana\","
                + "\"phone\":\"050-1234567\",\"description\":\"Study partner in CS101\"},"
                + "{\"name\":\"Lee\",\"whereKnown\":\"Army\"}]}");

        mvc.perform(get(companies(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.contacts", hasSize(2)))
                .andExpect(jsonPath("$.contacts[0].name").value("Dana"))
                .andExpect(jsonPath("$.contacts[0].whereKnown").value("TAU"))
                .andExpect(jsonPath("$.contacts[0].linkedin").value("https://linkedin.com/in/dana"))
                .andExpect(jsonPath("$.contacts[0].phone").value("050-1234567"))
                .andExpect(jsonPath("$.contacts[0].description").value("Study partner in CS101"))
                .andExpect(jsonPath("$.contacts[1].name").value("Lee"))
                .andExpect(jsonPath("$.contacts[1].phone").value(nullValue()));
    }

    @Test
    public void GivenCompanyWithoutContactsOrOnlyBlankOnes_WhenGetting_ThenContactsAreEmpty() throws Exception {
        Account account = newAccount();

        long bare = createCompany(account, "{\"name\":\"Bare\"}");
        long blank = createCompany(account,
                "{\"name\":\"Blank\",\"contacts\":[{\"name\":\" \",\"phone\":\"\"},null]}");

        mvc.perform(get(companies(account) + "/" + bare).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.contacts", hasSize(0)))
                .andExpect(jsonPath("$.jobs", hasSize(0)));
        mvc.perform(get(companies(account) + "/" + blank).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.contacts", hasSize(0)));
    }

    @Test
    public void GivenMissingOrBlankName_WhenCreatingCompany_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(companies(account)), account, "{\"contacts\":[{\"name\":\"Dana\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post(companies(account)), account, "{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenDuplicateName_WhenCreatingCompany_ThenConflictIgnoringCase() throws Exception {
        Account account = newAccount();
        createCompany(account, "{\"name\":\"Acme\"}");

        mvc.perform(json(post(companies(account)), account, "{\"name\":\"acme\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    public void GivenJobsAtCompany_WhenGettingCompany_ThenOnlyMatchingJobsOfThatUserListed() throws Exception {
        Account account = newAccount();
        Account other = newAccount();
        createJob(account, "Acme", "Backend dev");
        createJob(account, "ACME ", "Frontend dev");
        createJob(account, "Globex", "Analyst");
        createJob(other, "Acme", "Not mine");
        long id = createCompany(account, "{\"name\":\"Acme\"}");

        mvc.perform(get(companies(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.jobs", hasSize(2)))
                .andExpect(jsonPath("$.jobs[*].title", containsInAnyOrder("Backend dev", "Frontend dev")));
    }

    @Test
    public void GivenExistingCompany_WhenUpdating_ThenNameAndContactListAreReplaced() throws Exception {
        Account account = newAccount();
        long id = createCompany(account,
                "{\"name\":\"Acme\",\"contacts\":[{\"name\":\"Dana\"},{\"name\":\"Sam\"}]}");

        mvc.perform(json(put(companies(account) + "/" + id), account,
                "{\"name\":\"Acme Inc\",\"contacts\":[{\"name\":\"Lee\",\"phone\":\"1\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Inc"))
                .andExpect(jsonPath("$.contacts", hasSize(1)))
                .andExpect(jsonPath("$.contacts[0].name").value("Lee"))
                .andExpect(jsonPath("$.contacts[0].phone").value("1"));
        mvc.perform(json(put(companies(account) + "/" + id), account, "{\"name\":\"Acme Inc\"}"))
                .andExpect(jsonPath("$.contacts", hasSize(0)));
    }

    @Test
    public void GivenCompaniesOfTwoUsers_WhenListing_ThenOnlyOwnReturnedSortedByName() throws Exception {
        Account account = newAccount();
        Account other = newAccount();
        createCompany(account, "{\"name\":\"Beta\"}");
        createCompany(account, "{\"name\":\"Alpha\"}");
        createCompany(other, "{\"name\":\"Zeta\"}");

        mvc.perform(get(companies(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[1].name").value("Beta"));
    }

    @Test
    public void GivenCompany_WhenDeleting_ThenGoneButJobsRemain() throws Exception {
        Account account = newAccount();
        createJob(account, "Acme", "Backend dev");
        long id = createCompany(account, "{\"name\":\"Acme\"}");

        mvc.perform(delete(companies(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(companies(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/users/" + account.id() + "/jobs").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    public void GivenCompanyOfAnotherUser_WhenAccessingIt_ThenNotFoundOrForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = createCompany(owner, "{\"name\":\"Acme\"}");

        mvc.perform(get(companies(other) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get(companies(owner) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

}
