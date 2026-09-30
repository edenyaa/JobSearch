package com.ats;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class CourseApiTest extends ApiTestSupport {

    private String courses(Account account) {
        return "/api/users/" + account.id() + "/courses";
    }

    private long createSkill(Account account, String name) throws Exception {
        String response = mvc.perform(json(post("/api/users/" + account.id() + "/skills"), account,
                "{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    private long createCourse(Account account, String body) throws Exception {
        String response = mvc.perform(json(post(courses(account)), account, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    @Test
    public void GivenCourseWithAssignmentsAndSkills_WhenCreatingAndGetting_ThenAllFieldsReturned() throws Exception {
        Account account = newAccount();
        long java = createSkill(account, "Java");
        long sql = createSkill(account, "SQL");

        long id = createCourse(account, "{\"courseNumber\":\"CS101\",\"institution\":\"TAU\",\"description\":\"Intro\","
                + "\"assignments\":[\"Built a compiler\",\"Wrote a shell\"],\"skillIds\":[" + java + "," + sql + "]}");

        mvc.perform(get(courses(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseNumber").value("CS101"))
                .andExpect(jsonPath("$.institution").value("TAU"))
                .andExpect(jsonPath("$.description").value("Intro"))
                .andExpect(jsonPath("$.assignments", contains("Built a compiler", "Wrote a shell")))
                .andExpect(jsonPath("$.skillIds", containsInAnyOrder((int) java, (int) sql)));
    }

    @Test
    public void GivenCourseWithOnlyCourseNumber_WhenGetting_ThenListsAreEmpty() throws Exception {
        Account account = newAccount();

        long id = createCourse(account, "{\"courseNumber\":\"X1\"}");

        mvc.perform(get(courses(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.assignments", hasSize(0)))
                .andExpect(jsonPath("$.skillIds", hasSize(0)));
    }

    @Test
    public void GivenMissingOrBlankCourseNumber_WhenCreatingCourse_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(courses(account)), account, "{\"institution\":\"TAU\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post(courses(account)), account, "{\"courseNumber\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenUnknownSkillId_WhenCreatingCourse_ThenNotFound() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post(courses(account)), account, "{\"courseNumber\":\"X\",\"skillIds\":[999999]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenSkillOfAnotherUser_WhenCreatingCourseWithIt_ThenNotFound() throws Exception {
        Account account = newAccount();
        Account other = newAccount();
        long foreignSkill = createSkill(other, "Java");

        mvc.perform(json(post(courses(account)), account,
                "{\"courseNumber\":\"X\",\"skillIds\":[" + foreignSkill + "]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenExistingCourse_WhenUpdating_ThenFieldsAndSkillLinksAreReplaced() throws Exception {
        Account account = newAccount();
        long first = createSkill(account, "S1");
        long second = createSkill(account, "S2");
        long id = createCourse(account, "{\"courseNumber\":\"C1\",\"assignments\":[\"old\"],\"skillIds\":[" + first + "]}");

        mvc.perform(json(put(courses(account) + "/" + id), account,
                "{\"courseNumber\":\"C2\",\"assignments\":[\"new\"],\"skillIds\":[" + second + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseNumber").value("C2"))
                .andExpect(jsonPath("$.assignments", contains("new")))
                .andExpect(jsonPath("$.skillIds", contains((int) second)));
    }

    @Test
    public void GivenCoursesOfTwoUsers_WhenListing_ThenOnlyOwnCoursesReturned() throws Exception {
        Account account = newAccount();
        Account other = newAccount();
        createCourse(account, "{\"courseNumber\":\"A1\"}");
        createCourse(other, "{\"courseNumber\":\"B1\"}");

        mvc.perform(get(courses(account)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseNumber").value("A1"));
    }

    @Test
    public void GivenCourseWithSkill_WhenDeletingCourse_ThenSkillStillExists() throws Exception {
        Account account = newAccount();
        long skill = createSkill(account, "Java");
        long id = createCourse(account, "{\"courseNumber\":\"C\",\"skillIds\":[" + skill + "]}");

        mvc.perform(delete(courses(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(courses(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/users/" + account.id() + "/skills/" + skill).header("Authorization", account.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    public void GivenSkillLinkedToCourse_WhenDeletingSkill_ThenItIsUnlinkedFromCourse() throws Exception {
        Account account = newAccount();
        long keep = createSkill(account, "Keep");
        long drop = createSkill(account, "Drop");
        long id = createCourse(account, "{\"courseNumber\":\"C\",\"skillIds\":[" + keep + "," + drop + "]}");

        mvc.perform(delete("/api/users/" + account.id() + "/skills/" + drop).header("Authorization", account.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(courses(account) + "/" + id).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillIds", contains((int) keep)));
    }

    @Test
    public void GivenUnknownCourseId_WhenGettingOrDeleting_ThenNotFound() throws Exception {
        Account account = newAccount();

        mvc.perform(get(courses(account) + "/999999").header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(courses(account) + "/999999").header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenCourseOfAnotherUser_WhenAccessingIt_ThenNotFoundOrForbidden() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = createCourse(owner, "{\"courseNumber\":\"A1\"}");

        mvc.perform(get(courses(other) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get(courses(owner) + "/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

}
