package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.TailoredResumePayload;
import com.ats.service.GeminiClient;
import com.ats.service.TailoringPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiPipelineLiveTest {

    private static final String JOB = """
            Backend Engineer. Required: 3+ years of Java, Spring Boot, PostgreSQL, Docker and REST APIs.
            Nice to have: Kubernetes. You will design microservices.
            """;

    private static final String RESUME = """
            [[NAME]]
            [[EMAIL_1]]
            SUMMARY
            Backend developer.
            SKILLS
            Java, Spring, Postgres, Docker, Git
            EXPERIENCE
            Backend Developer, Acme Ltd, 2021 - 2024
            - Wrote REST APIs in Java and Spring for the billing system
            - Tuned Postgres queries and cut report time from 40s to 6s
            PROJECTS
            Pet shop site (Java, Spring, Docker)
            - Built an online store with a cart and checkout
            EDUCATION
            BSc Computer Science, Tel Aviv University, 2017 - 2020
            """;

    private TailoringPipeline pipeline() {
        JsonMapper mapper = JsonMapper.builder().build();
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash");
        GeminiClient client = new GeminiClient(mapper, System.getenv("GEMINI_API_KEY"), model,
                "https://generativelanguage.googleapis.com", 120, 3, 4000,
                "gemini-3.1-flash-lite,gemini-flash-lite-latest");
        return new TailoringPipeline(client, mapper);
    }

    @Test
    public void GivenRealApiKey_WhenRunningThePipeline_ThenResumeIsGroundedAndNamesAreNormalized() {
        TailoredResumePayload payload = pipeline().run("Backend Engineer", "Acme", JOB, RESUME, List.of(), List.of());

        assertFalse(payload.resume().experience().isEmpty());
        assertFalse(payload.resume().projects().isEmpty());
        assertTrue(payload.resume().plainText().contains("PostgreSQL"));
        assertFalse(payload.resume().plainText().contains("Postgres "));
        assertFalse(payload.resume().plainText().contains("Kubernetes"));
        assertTrue(payload.gap().matchedSkills().stream().anyMatch(k -> k.equalsIgnoreCase("Java")));
        assertTrue(payload.resume().plainText().contains("40s"));
    }

}
