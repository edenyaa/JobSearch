package com.ats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class CorsApiTest extends ApiTestSupport {

    private static final String FRONTEND = "http://localhost:5173";

    @Test
    public void GivenAllowedOrigin_WhenSendingPreflight_ThenAuthorizationHeaderIsAllowedWithoutLogin() throws Exception {
        mvc.perform(options("/api/users/1/jobs")
                .header(HttpHeaders.ORIGIN, FRONTEND)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization"));
    }

    @Test
    public void GivenUnknownOrigin_WhenSendingPreflight_ThenItIsRejected() throws Exception {
        mvc.perform(options("/api/users/1/jobs")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void GivenAllowedOrigin_WhenLoggingIn_ThenResponseCarriesTheOriginHeader() throws Exception {
        Account account = newAccount();

        mvc.perform(get("/api/users/" + account.id() + "/jobs")
                .header(HttpHeaders.ORIGIN, FRONTEND)
                .header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition"));
    }

}
