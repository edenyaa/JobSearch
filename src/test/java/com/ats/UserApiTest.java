package com.ats;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class UserApiTest extends ApiTestSupport {

    @Test
    public void GivenValidDetails_WhenRegistering_ThenCreatedWithoutPasswordInResponse() throws Exception {
        String body = registerBody("Eden", uniqueEmail(), PASSWORD);

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Eden"))
                .andExpect(jsonPath("$.hasMainCv").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    public void GivenRegisteredEmailInOtherCase_WhenRegisteringAgain_ThenConflict() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(registerBody("A", email, PASSWORD)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("B", email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    public void GivenInvalidEmailWeakPasswordOrMissingFields_WhenRegistering_ThenBadRequest() throws Exception {
        String badEmail = registerBody("A", "bad", PASSWORD);
        String weakPassword = registerBody("A", uniqueEmail(), "weak");
        String blankName = registerBody("", uniqueEmail(), PASSWORD);

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(badEmail))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(weakPassword))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(blankName))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenRegisteredUser_WhenLoggingInWithEmailInOtherCase_ThenTokenAndUserReturned() throws Exception {
        Account account = newAccount();

        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(account.email().toUpperCase(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.id").value(account.id()));
    }

    @Test
    public void GivenWrongPasswordOrUnknownEmail_WhenLoggingIn_ThenUnauthorized() throws Exception {
        Account account = newAccount();

        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(account.email(), "wrong1234")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void GivenMissingFields_WhenLoggingIn_ThenBadRequest() throws Exception {
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenNoToken_WhenCallingProtectedEndpoints_ThenUnauthorized() throws Exception {
        Account account = newAccount();
        String base = "/api/users/" + account.id();

        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/skills")).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/jobs")).andExpect(status().isUnauthorized());
    }

    @Test
    public void GivenGarbageTokenOrMissingBearerPrefix_WhenCallingProtectedEndpoint_ThenUnauthorized() throws Exception {
        Account account = newAccount();
        String url = "/api/users/" + account.id();

        mvc.perform(get(url).header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", account.token())).andExpect(status().isUnauthorized());
    }

    @Test
    public void GivenValidToken_WhenGettingOwnUser_ThenOk() throws Exception {
        Account account = newAccount();

        mvc.perform(get("/api/users/" + account.id()).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.email()));
    }

    @Test
    public void GivenTokenOfAnotherUser_WhenCallingProtectedEndpoints_ThenForbidden() throws Exception {
        Account owner = newAccount();
        Account intruder = newAccount();
        String base = "/api/users/" + owner.id();

        mvc.perform(get(base).header("Authorization", intruder.bearer())).andExpect(status().isForbidden());
        mvc.perform(get(base + "/skills").header("Authorization", intruder.bearer())).andExpect(status().isForbidden());
        mvc.perform(delete(base).header("Authorization", intruder.bearer())).andExpect(status().isForbidden());
    }

    @Test
    public void GivenTwoSessions_WhenLoggingOutOne_ThenOnlyThatSessionIsRejected() throws Exception {
        Account account = newAccount();
        String secondToken = login(account.email(), PASSWORD);
        String url = "/api/users/" + account.id();

        mvc.perform(post(url + "/logout").header("Authorization", account.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(url).header("Authorization", account.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer " + secondToken)).andExpect(status().isOk());
    }

    @Test
    public void GivenRightOldName_WhenChangingName_ThenNameIsUpdated() throws Exception {
        Account account = newAccount();

        mvc.perform(json(put("/api/users/" + account.id() + "/name"), account,
                "{\"oldValue\":\"Eden\",\"newValue\":\"Eden Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Eden Y"));
    }

    @Test
    public void GivenWrongOldName_WhenChangingName_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(json(put("/api/users/" + account.id() + "/name"), account,
                "{\"oldValue\":\"Wrong\",\"newValue\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenRightOldEmail_WhenChangingEmail_ThenOnlyNewEmailCanLogIn() throws Exception {
        Account account = newAccount();
        String newEmail = uniqueEmail();

        mvc.perform(json(put("/api/users/" + account.id() + "/email"), account,
                "{\"oldValue\":\"" + account.email() + "\",\"newValue\":\"" + newEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));

        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(newEmail, PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(account.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void GivenEmailOfAnotherUser_WhenChangingEmail_ThenConflict() throws Exception {
        Account account = newAccount();
        Account other = newAccount();

        mvc.perform(json(put("/api/users/" + account.id() + "/email"), account,
                "{\"oldValue\":\"" + account.email() + "\",\"newValue\":\"" + other.email() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    public void GivenWrongOldEmailOrInvalidNewEmail_WhenChangingEmail_ThenBadRequest() throws Exception {
        Account account = newAccount();
        String url = "/api/users/" + account.id() + "/email";

        mvc.perform(json(put(url), account, "{\"oldValue\":\"other@x.com\",\"newValue\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(url), account, "{\"oldValue\":\"" + account.email() + "\",\"newValue\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenWrongOldOrWeakNewPassword_WhenChangingPassword_ThenBadRequestAndSessionStaysValid() throws Exception {
        Account account = newAccount();
        String url = "/api/users/" + account.id() + "/password";

        mvc.perform(json(put(url), account, "{\"oldValue\":\"wrongold1\",\"newValue\":\"brandnew123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(url), account, "{\"oldValue\":\"" + PASSWORD + "\",\"newValue\":\"weak\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/users/" + account.id()).header("Authorization", account.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    public void GivenRightOldPassword_WhenChangingPassword_ThenAllSessionsEndAndNewPasswordWorks() throws Exception {
        Account account = newAccount();
        String secondToken = login(account.email(), PASSWORD);
        String url = "/api/users/" + account.id();

        mvc.perform(json(put(url + "/password"), account,
                "{\"oldValue\":\"" + PASSWORD + "\",\"newValue\":\"brandnew123\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(url).header("Authorization", account.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer " + secondToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(account.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(account.email(), "brandnew123")))
                .andExpect(status().isOk());
    }

    @Test
    public void GivenNoCvUploaded_WhenDownloadingCv_ThenNotFound() throws Exception {
        Account account = newAccount();

        mvc.perform(get("/api/users/" + account.id() + "/cv").header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenUploadedCv_WhenDownloadingCv_ThenSameBytesAndTypeReturned() throws Exception {
        Account account = newAccount();
        String url = "/api/users/" + account.id() + "/cv";
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", "PDF-BYTES".getBytes());

        mvc.perform(multipart(HttpMethod.PUT, url).file(file).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMainCv").value(true))
                .andExpect(jsonPath("$.mainCvFileName").value("cv.pdf"));

        mvc.perform(get(url).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("cv.pdf")))
                .andExpect(content().bytes("PDF-BYTES".getBytes()));
    }

    @Test
    public void GivenUploadedCv_WhenUploadingAnother_ThenItIsReplaced() throws Exception {
        Account account = newAccount();
        String url = "/api/users/" + account.id() + "/cv";
        MockMultipartFile first = new MockMultipartFile("file", "one.txt", "text/plain", "one".getBytes());
        MockMultipartFile second = new MockMultipartFile("file", "two.txt", "text/plain", "two".getBytes());

        mvc.perform(multipart(HttpMethod.PUT, url).file(first).header("Authorization", account.bearer()))
                .andExpect(status().isOk());
        mvc.perform(multipart(HttpMethod.PUT, url).file(second).header("Authorization", account.bearer()))
                .andExpect(status().isOk());

        mvc.perform(get(url).header("Authorization", account.bearer()))
                .andExpect(content().bytes("two".getBytes()));
    }

    @Test
    public void GivenUserWithData_WhenDeletingUser_ThenSessionAndLoginAreGone() throws Exception {
        Account account = newAccount();
        String url = "/api/users/" + account.id();
        mvc.perform(json(post(url + "/skills"), account, "{\"name\":\"Java\"}")).andExpect(status().isCreated());

        mvc.perform(delete(url).header("Authorization", account.bearer())).andExpect(status().isNoContent());

        mvc.perform(get(url).header("Authorization", account.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(account.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

}
