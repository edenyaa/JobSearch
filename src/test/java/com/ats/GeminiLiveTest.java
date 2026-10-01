package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.service.GeminiClient;
import com.ats.service.LlmException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiLiveTest {

    private GeminiClient realClient() {
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash");
        return new GeminiClient(JsonMapper.builder().build(), System.getenv("GEMINI_API_KEY"), model,
                "https://generativelanguage.googleapis.com", 120, 3, 4000,
                "gemini-3.1-flash-lite,gemini-flash-lite-latest");
    }

    private boolean isTemporary(LlmException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return message.contains("rate limit") || message.contains("HTTP 503") || message.contains("HTTP 500")
                || message.contains("HTTP 504") || message.contains("Could not reach");
    }

    @Test
    public void GivenRealApiKey_WhenAskingForJson_ThenGeminiReturnsParsableJson() {
        String text = null;
        try {
            text = realClient().generateJson(
                    "Reply with JSON only.",
                    "Return an object with one field named \"greeting\" whose value is the word hello.");
        } catch (LlmException e) {
            Assumptions.assumeFalse(isTemporary(e), "Gemini is busy or rate limited: " + e.getMessage());
            throw e;
        }

        JsonMapper mapper = JsonMapper.builder().build();
        assertEquals("hello", mapper.readTree(text).path("greeting").asString("").toLowerCase());
    }

}
