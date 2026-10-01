package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.service.GeminiClient;
import com.ats.service.LlmException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GeminiClientTest {

    private static final String KEY = "secret-key-123";

    private HttpServer server;
    private int status;
    private String responseBody;
    private String lastPath;
    private String lastKey;
    private String lastBody;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    public void startServer() throws IOException {
        status = 200;
        responseBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}";
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath = exchange.getRequestURI().getPath();
            lastKey = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void stopServer() {
        server.stop(0);
    }

    private GeminiClient client(String key) {
        return new GeminiClient(JsonMapper.builder().build(), key, "test-model",
                "http://localhost:" + server.getAddress().getPort(), 10);
    }

    @Test
    public void GivenValidResponse_WhenGenerating_ThenGeneratedTextIsReturned() {
        String text = client(KEY).generateJson("be helpful", "hello");

        assertEquals("{\"ok\":true}", text);
    }

    @Test
    public void GivenRequest_WhenGenerating_ThenKeyModelJsonModeAndPromptsAreSent() {
        client(KEY).generateJson("system rules here", "user prompt here");

        assertEquals("/v1beta/models/test-model:generateContent", lastPath);
        assertEquals(KEY, lastKey);
        assertTrue(lastBody.contains("system rules here"));
        assertTrue(lastBody.contains("user prompt here"));
        assertTrue(lastBody.contains("application/json"));
        assertTrue(lastBody.contains("systemInstruction"));
        assertFalse(lastBody.contains(KEY));
    }

    @Test
    public void GivenMissingApiKey_WhenGenerating_ThenLlmExceptionAndNoRequestIsSent() {
        LlmException blank = assertThrows(LlmException.class, () -> client("").generateJson("s", "u"));
        LlmException absent = assertThrows(LlmException.class, () -> client(null).generateJson("s", "u"));

        assertTrue(blank.getMessage().contains("GEMINI_API_KEY"));
        assertTrue(absent.getMessage().contains("GEMINI_API_KEY"));
        assertEquals(0, calls.get());
    }

    @Test
    public void GivenRateLimit429_WhenGenerating_ThenLlmExceptionMentionsRateLimit() {
        status = 429;
        responseBody = "{}";

        LlmException e = assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));

        assertTrue(e.getMessage().toLowerCase().contains("rate limit"));
    }

    @Test
    public void GivenRejectedKey_WhenGenerating_ThenLlmExceptionWithoutLeakingTheKey() {
        status = 403;
        responseBody = "{}";

        LlmException e = assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));

        assertTrue(e.getMessage().contains("rejected"));
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    public void GivenServerError_WhenGenerating_ThenLlmExceptionWithStatusCode() {
        status = 500;
        responseBody = "{}";

        LlmException e = assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));

        assertTrue(e.getMessage().contains("500"));
    }

    private GeminiClient retryingClient(int maxRetries) {
        return new GeminiClient(JsonMapper.builder().build(), KEY, "test-model",
                "http://localhost:" + server.getAddress().getPort(), 10, maxRetries, 1);
    }

    private GeminiClient clientWithFallbacks(String fallbacks) {
        return new GeminiClient(JsonMapper.builder().build(), KEY, "primary-model",
                "http://localhost:" + server.getAddress().getPort(), 10, 0, 0, fallbacks);
    }

    private java.util.List<String> respondPerModel(java.util.Map<String, Integer> statusByModel) {
        java.util.List<String> requestedPaths = new java.util.concurrent.CopyOnWriteArrayList<>();
        String success = responseBody;
        server.removeContext("/");
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            int code = statusByModel.entrySet().stream().filter(e -> path.contains(e.getKey()))
                    .map(java.util.Map.Entry::getValue).findFirst().orElse(200);
            byte[] bytes = (code == 200 ? success : "{\"error\":{\"message\":\"nope\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        return requestedPaths;
    }

    @Test
    public void GivenPrimaryModelOverloaded_WhenGenerating_ThenFallbackModelIsUsed() {
        java.util.List<String> paths = respondPerModel(java.util.Map.of("primary-model", 503));

        String text = clientWithFallbacks("backup-one").generateJson("s", "u");

        assertEquals("{\"ok\":true}", text);
        assertEquals(2, paths.size());
        assertTrue(paths.get(0).contains("primary-model"));
        assertTrue(paths.get(1).contains("backup-one"));
    }

    @Test
    public void GivenPrimaryModelRetired404_WhenGenerating_ThenFallbackModelIsUsed() {
        java.util.List<String> paths = respondPerModel(java.util.Map.of("primary-model", 404));

        String text = clientWithFallbacks("backup-one").generateJson("s", "u");

        assertEquals("{\"ok\":true}", text);
        assertTrue(paths.get(1).contains("backup-one"));
    }

    @Test
    public void GivenSeveralFallbacks_WhenEarlierOnesFail_ThenTheFirstWorkingOneIsUsedAndTheRestSkipped() {
        java.util.List<String> paths = respondPerModel(java.util.Map.of("primary-model", 503, "backup-one", 503));

        String text = clientWithFallbacks(" backup-one , backup-two , backup-three").generateJson("s", "u");

        assertEquals("{\"ok\":true}", text);
        assertEquals(3, paths.size());
        assertTrue(paths.get(2).contains("backup-two"));
    }

    @Test
    public void GivenPrimaryModelWorks_WhenGenerating_ThenFallbacksAreNotCalled() {
        java.util.List<String> paths = respondPerModel(java.util.Map.of());

        clientWithFallbacks("backup-one").generateJson("s", "u");

        assertEquals(1, paths.size());
    }

    @Test
    public void GivenAllModelsOverloaded_WhenGenerating_ThenLlmExceptionIsThrownAfterTryingEach() {
        java.util.List<String> paths = respondPerModel(java.util.Map.of("primary-model", 503, "backup-one", 503));

        LlmException e = assertThrows(LlmException.class, () -> clientWithFallbacks("backup-one").generateJson("s", "u"));

        assertTrue(e.getMessage().contains("503"));
        assertEquals(2, paths.size());
    }

    @Test
    public void GivenRejectedKeyOnPrimary_WhenGeneratingWithFallbacks_ThenFallbackIsNotTried() {
        java.util.List<String> paths = respondPerModel(java.util.Map.of("primary-model", 403));

        assertThrows(LlmException.class, () -> clientWithFallbacks("backup-one").generateJson("s", "u"));

        assertEquals(1, paths.size());
    }

    @Test
    public void GivenTemporaryOverload503ThenSuccess_WhenGenerating_ThenItRetriesAndReturnsText() {
        String success = responseBody;
        server.removeContext("/");
        AtomicInteger seen = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            boolean overloaded = seen.incrementAndGet() <= 2;
            byte[] bytes = (overloaded ? "{\"error\":{\"message\":\"high demand\"}}" : success).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(overloaded ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        String text = retryingClient(3).generateJson("s", "u");

        assertEquals("{\"ok\":true}", text);
        assertEquals(3, calls.get());
    }

    @Test
    public void GivenPermanentOverload_WhenGenerating_ThenItGivesUpAfterTheConfiguredRetries() {
        status = 503;
        responseBody = "{\"error\":{\"message\":\"high demand\"}}";

        LlmException e = assertThrows(LlmException.class, () -> retryingClient(2).generateJson("s", "u"));

        assertTrue(e.getMessage().contains("503"));
        assertTrue(e.getMessage().contains("high demand"));
        assertEquals(3, calls.get());
    }

    @Test
    public void GivenRateLimit429_WhenGeneratingWithRetries_ThenItIsRetriedToo() {
        status = 429;
        responseBody = "{}";

        assertThrows(LlmException.class, () -> retryingClient(2).generateJson("s", "u"));

        assertEquals(3, calls.get());
    }

    @Test
    public void GivenClientErrorsLikeBadKeyOrUnknownModel_WhenGeneratingWithRetries_ThenTheyAreNotRetried() {
        status = 404;
        responseBody = "{}";
        assertThrows(LlmException.class, () -> retryingClient(3).generateJson("s", "u"));
        status = 403;
        assertThrows(LlmException.class, () -> retryingClient(3).generateJson("s", "u"));

        assertEquals(2, calls.get());
    }

    @Test
    public void GivenModelNoLongerAvailable_WhenGenerating_ThenGooglesErrorMessageIsIncluded() {
        status = 404;
        responseBody = "{\"error\":{\"code\":404,\"message\":\"This model is no longer available to new users.\"}}";

        LlmException e = assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));

        assertTrue(e.getMessage().contains("404"));
        assertTrue(e.getMessage().contains("no longer available"));
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    public void GivenVeryLongErrorMessage_WhenGenerating_ThenItIsTruncated() {
        status = 400;
        responseBody = "{\"error\":{\"message\":\"" + "x".repeat(2000) + "\"}}";

        LlmException e = assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));

        assertTrue(e.getMessage().length() < 400);
    }

    @Test
    public void GivenThinkingPartBeforeAnswer_WhenGenerating_ThenOnlyAnswerTextIsReturned() {
        responseBody = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"internal reasoning\",\"thought\":true},"
                + "{\"text\":\"{\\\"a\\\":\"},{\"text\":\"1}\"}]}}]}";

        String text = client(KEY).generateJson("s", "u");

        assertEquals("{\"a\":1}", text);
    }

    @Test
    public void GivenBodyThatIsNotJson_WhenGenerating_ThenLlmException() {
        responseBody = "<html>oops</html>";

        assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));
    }

    @Test
    public void GivenBlockedPrompt_WhenGenerating_ThenLlmExceptionMentionsBlockReason() {
        responseBody = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";

        LlmException e = assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));

        assertTrue(e.getMessage().contains("SAFETY"));
    }

    @Test
    public void GivenCandidateWithoutText_WhenGenerating_ThenLlmExceptionMentionsFinishReason() {
        responseBody = "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}";

        LlmException e = assertThrows(LlmException.class, () -> client(KEY).generateJson("s", "u"));

        assertTrue(e.getMessage().contains("MAX_TOKENS"));
    }

    @Test
    public void GivenServerNotReachable_WhenGenerating_ThenLlmExceptionAndNoKeyLeak() {
        int closedPort = server.getAddress().getPort();
        server.stop(0);
        GeminiClient client = new GeminiClient(JsonMapper.builder().build(), KEY, "test-model",
                "http://localhost:" + closedPort, 5);

        LlmException e = assertThrows(LlmException.class, () -> client.generateJson("s", "u"));

        assertTrue(e.getMessage().contains("Could not reach Gemini"));
        assertFalse(e.getMessage().contains(KEY));
    }

}
