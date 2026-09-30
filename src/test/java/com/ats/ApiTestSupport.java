package com.ats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
abstract class ApiTestSupport {

    static final String PASSWORD = "secret123";

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    public void setUpMvc() {
        mvc = webAppContextSetup(context).build();
    }

    record Account(long id, String email, String token) {

        String bearer() {
            return "Bearer " + token;
        }
    }

    String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    String registerBody(String name, String email, String password) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    Account newAccount() throws Exception {
        String email = uniqueEmail();
        String created = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Eden", email, PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        long id = idOf(created);
        return new Account(id, email, login(email, PASSWORD));
    }

    String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Account who, String body) {
        return request.header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    static long idOf(String responseBody) {
        return ((Number) JsonPath.read(responseBody, "$.id")).longValue();
    }

}
