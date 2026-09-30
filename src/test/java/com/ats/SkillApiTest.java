package com.ats;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class SkillApiTest extends ApiTestSupport {

    private String base(Account account) {
        return "/api/users/" + account.id() + "/skills";
    }

    private long createSkill(Account account, String body) throws Exception {
        String response = mvc.perform(json(post(base(account)), account, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    @Test
    public void GivenSkillWithKeywords_WhenCreatingAndGetting_ThenAllFieldsReturned() throws Exception {
        Account account = newAccount();

        long id = createSkill(account, "{\"name\":\"Java\",\"explanation\":\"Backend\",\"keywords\":[\"Spring\",\"JPA\"]}");

        mvc.perform(get(base(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Java"))
                .andExpect(jsonPath("$.explanation").value("Backend"))
                .andExpect(jsonPath("$.keywords", contains("Spring", "JPA")));
    }

    @Test
    public void GivenSkillWithoutKeywords_WhenGetting_ThenKeywordsIsEmptyList() throws Exception {
        Account account = newAccount();

        long id = createSkill(account, "{\"name\":\"SQL\"}");

        mvc.perform(get(base(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.keywords", hasSize(0)));
    }

    @Test
    public void GivenMissingOrBlankName_WhenCreatingSkill_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(base(account)), account, "{\"explanation\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post(base(account)), account, "{\"name\":\"  \"}")).andExpect(status().isBadRequest());
    }

    @Test
    public void GivenSkillsOfTwoUsers_WhenListing_ThenOnlyOwnSkillsReturned() throws Exception {
        Account account = newAccount();
        Account other = newAccount();
        createSkill(account, "{\"name\":\"A1\"}");
        createSkill(account, "{\"name\":\"A2\"}");
        createSkill(other, "{\"name\":\"B1\"}");

        mvc.perform(get(base(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("A1", "A2")));
    }

    @Test
    public void GivenExistingSkill_WhenUpdating_ThenFieldsAreReplaced() throws Exception {
        Account account = newAccount();
        long id = createSkill(account, "{\"name\":\"Java\",\"keywords\":[\"old\"]}");

        mvc.perform(json(put(base(account) + "/" + id), account,
                "{\"name\":\"Kotlin\",\"explanation\":\"e\",\"keywords\":[\"new1\",\"new2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kotlin"))
                .andExpect(jsonPath("$.keywords", contains("new1", "new2")));
    }

    @Test
    public void GivenBlankName_WhenUpdatingSkill_ThenBadRequest() throws Exception {
        Account account = newAccount();
        long id = createSkill(account, "{\"name\":\"Java\"}");

        mvc.perform(json(put(base(account) + "/" + id), account, "{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenExistingSkill_WhenDeleting_ThenItIsGone() throws Exception {
        Account account = newAccount();
        long id = createSkill(account, "{\"name\":\"Java\"}");

        mvc.perform(delete(base(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(base(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenUnknownSkillId_WhenGettingUpdatingOrDeleting_ThenNotFound() throws Exception {
        Account account = newAccount();
        String url = base(account) + "/999999";

        mvc.perform(get(url).header("Authorization", account.bearer())).andExpect(status().isNotFound());
        mvc.perform(json(put(url), account, "{\"name\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(delete(url).header("Authorization", account.bearer())).andExpect(status().isNotFound());
    }

    @Test
    public void GivenSkillOfAnotherUser_WhenAccessingItThroughOwnPath_ThenNotFound() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = createSkill(owner, "{\"name\":\"Secret\"}");

        mvc.perform(get(base(other) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(base(other) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenSkillOfAnotherUser_WhenAccessingItThroughOwnersPath_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = createSkill(owner, "{\"name\":\"Secret\"}");

        mvc.perform(get(base(owner) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get(base(owner) + "/" + id).header("Authorization", owner.bearer()))
                .andExpect(status().isOk());
    }

}
