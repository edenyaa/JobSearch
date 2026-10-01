package com.ats;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ats.service.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ErrorMessagesApiTest extends ApiTestSupport {

    @MockitoBean
    SkillService skills;

    @Test
    public void GivenJobWithoutDescription_WhenCreating_ThenMessageNamesTheBadField() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post("/api/users/" + account.id() + "/jobs"), account, "{\"jobNumber\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Invalid data: ")))
                .andExpect(jsonPath("$.message", containsString("description")));
    }

    @Test
    public void GivenBlankName_WhenRegistering_ThenMessageSaysWhatIsWrong() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("", uniqueEmail(), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid data: name must not be blank."));
    }

    @Test
    public void GivenMalformedJson_WhenPosting_ThenMessageSaysTheBodyIsNotValidJson() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post("/api/users/" + account.id() + "/jobs"), account, "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is missing or is not valid JSON."));
    }

    @Test
    public void GivenUnknownPath_WhenRequesting_ThenNotFoundWithAMessage() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", not(emptyString())));
    }

    @Test
    public void GivenWrongHttpMethod_WhenRequesting_ThenMethodNotAllowedWithAMessage() throws Exception {
        Account account = newAccount();

        mvc.perform(patch("/api/users/" + account.id() + "/skills").header("Authorization", account.bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message", not(emptyString())));
    }

    @Test
    public void GivenMissingUploadPart_WhenUploadingCv_ThenBadRequestWithAMessage() throws Exception {
        Account account = newAccount();

        mvc.perform(multipart(HttpMethod.PUT, "/api/users/" + account.id() + "/cv")
                .file(new MockMultipartFile("other", "x.txt", "text/plain", "x".getBytes()))
                .header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(emptyString())));
    }

    @Test
    public void GivenUnexpectedFailure_WhenRequesting_ThenMessageNamesTheTypeAndCause() throws Exception {
        Account account = newAccount();
        when(skills.list(account.id())).thenThrow(new IllegalStateException("database file is locked"));

        mvc.perform(get("/api/users/" + account.id() + "/skills").header("Authorization", account.bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message", containsString("IllegalStateException")))
                .andExpect(jsonPath("$.message", containsString("database file is locked")));
    }

    @Test
    public void GivenUnexpectedFailureWithNestedCause_WhenRequesting_ThenRootCauseIsReported() throws Exception {
        Account account = newAccount();
        when(skills.list(account.id())).thenThrow(new RuntimeException("outer", new IllegalArgumentException("root problem")));

        mvc.perform(get("/api/users/" + account.id() + "/skills").header("Authorization", account.bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("IllegalArgumentException")))
                .andExpect(jsonPath("$.message", containsString("root problem")));
    }

}
