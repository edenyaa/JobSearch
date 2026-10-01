package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiLiveTest {

    private GeminiClient realClient() {
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash");
        return new GeminiClient(JsonMapper.builder().build(), System.getenv("GEMINI_API_KEY"), model,
                "https://generativelanguage.googleapis.com", 120, 3, 4000);
    }

    @Test
    public void GivenRealApiKey_WhenAskingForJson_ThenGeminiReturnsParsableJson() {
        String text = realClient().generateJson(
                "Reply with JSON only.",
                "Return an object with one field named \"greeting\" whose value is the word hello.");

        JsonMapper mapper = JsonMapper.builder().build();
        assertEquals("hello", mapper.readTree(text).path("greeting").asString("").toLowerCase());
    }

}
