package com.ats.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiClient implements LlmClient {

	private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
	private static final Set<Integer> TRY_NEXT_MODEL = Set.of(404, 429, 500, 502, 503, 504);

	private final JsonMapper mapper;
	private final HttpClient http = HttpClient.newHttpClient();
	private final String apiKey;
	private final List<String> models;
	private final String baseUrl;
	private final Duration timeout;
	private final int maxRetries;
	private final long retryDelayMillis;

	@Autowired
	public GeminiClient(JsonMapper mapper,
			@Value("${llm.gemini.api-key:}") String apiKey,
			@Value("${llm.gemini.model:gemini-3.5-flash}") String model,
			@Value("${llm.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
			@Value("${llm.gemini.timeout-seconds:120}") long timeoutSeconds,
			@Value("${llm.gemini.max-retries:2}") int maxRetries,
			@Value("${llm.gemini.retry-delay-millis:4000}") long retryDelayMillis,
			@Value("${llm.gemini.fallback-models:}") String fallbackModels) {
		this.mapper = mapper;
		this.apiKey = apiKey;
		this.models = modelList(model, fallbackModels);
		this.baseUrl = baseUrl;
		this.timeout = Duration.ofSeconds(timeoutSeconds);
		this.maxRetries = maxRetries;
		this.retryDelayMillis = retryDelayMillis;
	}

	public GeminiClient(JsonMapper mapper, String apiKey, String model, String baseUrl, long timeoutSeconds,
			int maxRetries, long retryDelayMillis) {
		this(mapper, apiKey, model, baseUrl, timeoutSeconds, maxRetries, retryDelayMillis, "");
	}

	public GeminiClient(JsonMapper mapper, String apiKey, String model, String baseUrl, long timeoutSeconds) {
		this(mapper, apiKey, model, baseUrl, timeoutSeconds, 0, 0, "");
	}

	@Override
	public String generateJson(String systemInstruction, String userPrompt) {
		return generateJson(systemInstruction, userPrompt, 0.3, null);
	}

	@Override
	public String generateJson(String systemInstruction, String userPrompt, double temperature,
			Map<String, Object> responseSchema) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new LlmException("GEMINI_API_KEY is not set on the server.");
		}
		Map<String, Object> generationConfig = new HashMap<>();
		generationConfig.put("responseMimeType", "application/json");
		generationConfig.put("temperature", temperature);
		if (responseSchema != null) {
			generationConfig.put("responseJsonSchema", responseSchema);
		}
		String body = mapper.writeValueAsString(Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
				"generationConfig", generationConfig));

		HttpResponse<String> response = null;
		for (int i = 0; i < models.size(); i++) {
			response = sendWithRetries(request(models.get(i), body));
			if (!TRY_NEXT_MODEL.contains(response.statusCode())) {
				break;
			}
		}
		return handle(response);
	}

	private String handle(HttpResponse<String> response) {
		if (response.statusCode() == 429) {
			throw new LlmException("Gemini rate limit reached. Wait a minute and try again.");
		}
		if (response.statusCode() == 401 || response.statusCode() == 403) {
			throw new LlmException("Gemini rejected the API key.");
		}
		if (response.statusCode() != 200) {
			throw new LlmException("Gemini returned HTTP " + response.statusCode() + errorDetail(response.body()));
		}
		return extractText(response.body());
	}

	private HttpRequest request(String model, String body) {
		return HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
				.timeout(timeout)
				.header("Content-Type", "application/json")
				.header("x-goog-api-key", apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
	}

	private List<String> modelList(String model, String fallbackModels) {
		List<String> list = new ArrayList<>();
		list.add(model);
		if (fallbackModels != null) {
			Arrays.stream(fallbackModels.split(",")).map(String::trim).filter(m -> !m.isEmpty()).forEach(list::add);
		}
		return List.copyOf(list);
	}

	private HttpResponse<String> sendWithRetries(HttpRequest request) {
		HttpResponse<String> response = send(request);
		for (int attempt = 1; attempt <= maxRetries && RETRYABLE.contains(response.statusCode()); attempt++) {
			pause(retryDelayMillis * attempt);
			response = send(request);
		}
		return response;
	}

	private void pause(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LlmException("The request to Gemini was interrupted.");
		}
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return http.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new LlmException("Could not reach Gemini: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LlmException("The request to Gemini was interrupted.");
		}
	}

	private String errorDetail(String responseBody) {
		try {
			String message = mapper.readTree(responseBody).path("error").path("message").asString("");
			if (message.isBlank()) {
				return ".";
			}
			return ": " + (message.length() > 300 ? message.substring(0, 300) + "..." : message);
		} catch (JacksonException e) {
			return ".";
		}
	}

	private String extractText(String responseBody) {
		JsonNode root;
		try {
			root = mapper.readTree(responseBody);
		} catch (JacksonException e) {
			throw new LlmException("Gemini returned a response that could not be read.");
		}
		JsonNode candidate = root.path("candidates").path(0);
		StringBuilder answer = new StringBuilder();
		for (JsonNode part : candidate.path("content").path("parts")) {
			if (!part.path("thought").asBoolean(false)) {
				answer.append(part.path("text").asString(""));
			}
		}
		String text = answer.toString();
		if (text.isBlank()) {
			String reason = candidate.path("finishReason").asString("");
			if (reason.isBlank()) {
				reason = root.path("promptFeedback").path("blockReason").asString("unknown");
			}
			throw new LlmException("Gemini returned no text (reason: " + reason + ").");
		}
		return text;
	}

}
