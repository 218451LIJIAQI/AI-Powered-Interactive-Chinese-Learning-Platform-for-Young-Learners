package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.example.aichinese.service.ai.OpenAiResponsesAiProviderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Unit coverage for {@code OpenAiResponsesAiProviderClient}, the default provider named in the
 * proposal (OpenAI Responses API, GPT-5.6 Luna): request shape, response parsing and the
 * never-throw failure contract (Proposal Table 6.2, AI-integration testing).
 */
class OpenAiResponsesAiProviderClientTests {

    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final String RESPONSES_PATH = "/responses";
    private static final String API_KEY = "test-api-key";
    private static final String MODEL_NAME = "gpt-5.6-luna";

    private static final String COMPLETED_RESPONSE = """
            {"id":"resp_1","status":"completed","output":[
              {"type":"reasoning","summary":[]},
              {"type":"message","role":"assistant","content":[
                {"type":"output_text","text":"你好 (nǐ hǎo) means hello."}]}],
             "usage":{"input_tokens":20,"output_tokens":12,"total_tokens":32}}
            """;
    private static final String INCOMPLETE_RESPONSE = """
            {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},
             "output":[{"type":"message","content":[{"type":"output_text","text":"{\\"word\\":"}]}]}
            """;
    private static final String FAILED_RESPONSE = """
            {"status":"failed","error":{"code":"server_error","message":"internal"},"output":[]}
            """;

    private final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();

    @Test
    void completedResponseBuildsResponsesPayloadAndExtractsOutputText() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext(RESPONSES_PATH, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, COMPLETED_RESPONSE);
        });
        server.start();
        try {
            AiProviderResult result = client(properties(server)).generateText(prompt(true));

            assertThat(result.success()).isTrue();
            assertThat(result.providerName()).isEqualTo("openai");
            assertThat(result.modelName()).isEqualTo(MODEL_NAME);
            assertThat(result.text()).isEqualTo("你好 (nǐ hǎo) means hello.");
            assertThat(result.tokenCount()).isEqualTo(32);
            assertThat(authorization.get()).isEqualTo("Bearer " + API_KEY);
            assertThat(requestBody.get())
                    .contains("\"model\":\"" + MODEL_NAME + "\"")
                    .contains("\"instructions\":\"Bounded beginner tutor instruction\"")
                    .contains("\"max_output_tokens\":64")
                    .contains("\"store\":false")
                    .contains("\"role\":\"assistant\"")
                    .contains("\"text\":{\"format\":{\"type\":\"json_object\"}}")
                    .doesNotContain("\"messages\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void incompleteJsonResponseIsRejectedInsteadOfReturningTruncatedJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext(RESPONSES_PATH, exchange -> sendJson(exchange, INCOMPLETE_RESPONSE));
        server.start();
        try {
            AiProviderResult result = client(properties(server)).generateText(prompt(true));

            assertThat(result.success()).isFalse();
            assertThat(result.errorSummary()).isEqualTo("Provider response was incomplete.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedProviderStatusReturnsControlledFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext(RESPONSES_PATH, exchange -> sendJson(exchange, FAILED_RESPONSE));
        server.start();
        try {
            AiProviderResult result = client(properties(server)).generateText(prompt(false));

            assertThat(result.success()).isFalse();
            assertThat(result.errorSummary()).isEqualTo("Provider response status was not completed.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void slowProviderTimesOutWithAControlledFailureWithinTheConfiguredTimeout() throws Exception {
        // Proposal Table 6.2 "AI - timeouts": the stub answers after the 1 s client timeout.
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext(RESPONSES_PATH, exchange -> {
            try {
                Thread.sleep(2_500L);
                sendJson(exchange, COMPLETED_RESPONSE);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client has already given up.
            }
        });
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        try {
            AiProperties properties = properties(server);
            properties.setTimeoutSeconds(1);
            long started = System.nanoTime();

            AiProviderResult result = client(properties).generateText(prompt(false));

            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            assertThat(result.success()).isFalse();
            assertThat(result.text()).isNull();
            assertThat(result.errorSummary()).isNotBlank();
            assertThat(elapsedMillis).as("the call is bounded by the timeout").isLessThan(2_400L);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void serverErrorAndRateLimitResponsesBecomeControlledFailures() throws Exception {
        for (int statusCode : new int[] {500, 503, 429}) {
            HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
            server.createContext(RESPONSES_PATH, exchange -> sendStatus(exchange, statusCode,
                    "{\"error\":{\"message\":\"provider unavailable\",\"code\":\"" + statusCode + "\"}}"));
            server.start();
            try {
                AiProviderResult result = client(properties(server)).generateText(prompt(true));

                assertThat(result.success()).as("HTTP " + statusCode + " is not a success").isFalse();
                assertThat(result.text()).isNull();
                assertThat(result.errorSummary()).as("HTTP " + statusCode + " has a safe error summary").isNotBlank();
                assertThat(result.errorSummary()).doesNotContain(API_KEY);
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void imagePromptIsSentAsInputTextPlusInputImageDataUrl() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext(RESPONSES_PATH, exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, COMPLETED_RESPONSE);
        });
        server.start();
        try {
            byte[] imageBytes = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
            AiProviderPrompt prompt = new AiProviderPrompt(
                    "WRITING_FEEDBACK",
                    "Bounded handwriting instruction",
                    "Give short handwriting practice feedback in JSON.",
                    "en",
                    64,
                    true,
                    List.of(),
                    new AiProviderPrompt.AiImageInput(imageBytes, "image/png"));

            AiProviderResult result = client(properties(server)).generateText(prompt);

            assertThat(result.success()).isTrue();
            String expectedDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
            JsonNode body = new ObjectMapper().readTree(requestBody.get());
            JsonNode content = body.path("input").get(0).path("content");
            assertThat(body.path("store").asBoolean(true)).isFalse();
            assertThat(content.isArray()).isTrue();
            assertThat(content.get(0).path("type").asText()).isEqualTo("input_text");
            assertThat(content.get(0).path("text").asText()).isEqualTo("Give short handwriting practice feedback in JSON.");
            assertThat(content.get(1).path("type").asText()).isEqualTo("input_image");
            assertThat(content.get(1).path("image_url").asText()).isEqualTo(expectedDataUrl);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatCompletionsClientReportsImageInputAsUnsupportedWithoutCallingProvider() throws Exception {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext("/chat/completions", exchange -> {
            called.set(true);
            sendJson(exchange, "{}");
        });
        server.start();
        try {
            AiProperties properties = properties(server);
            properties.setProvider("deepseek");
            AiProviderPrompt prompt = new AiProviderPrompt(
                    "WRITING_FEEDBACK", "Instruction", "Feedback please", "en", 64, true, List.of(),
                    new AiProviderPrompt.AiImageInput(new byte[] {1, 2, 3}, "image/jpeg"));

            AiProviderResult result = new OpenAiCompatibleAiProviderClient(properties, restTemplateBuilder)
                    .generateText(prompt);

            assertThat(result.success()).isFalse();
            assertThat(result.errorSummary()).isEqualTo("Image input is not supported by the configured provider.");
            assertThat(called.get()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void imageAnalysisIsAvailableOnlyForTheEnabledOpenAiProvider() {
        AiProperties openAi = new AiProperties();
        openAi.setEnableRealProvider(true);
        openAi.setApiKey(API_KEY);
        AiProperties deepseek = new AiProperties();
        deepseek.setEnableRealProvider(true);
        deepseek.setProvider("deepseek");
        deepseek.setApiBaseUrl("https://api.deepseek.com");
        deepseek.setModelName("deepseek-chat");
        deepseek.setApiKey(API_KEY);
        AiProperties disabledOpenAi = new AiProperties();
        disabledOpenAi.setApiKey(API_KEY);

        AiAvailabilityResponse openAiAvailability = new AiAvailabilityService(openAi).getAvailability();
        AiAvailabilityResponse deepseekAvailability = new AiAvailabilityService(deepseek).getAvailability();
        AiAvailabilityResponse disabledAvailability = new AiAvailabilityService(disabledOpenAi).getAvailability();

        assertThat(openAiAvailability.available()).isTrue();
        assertThat(openAiAvailability.imageAnalysisAvailable()).isTrue();
        assertThat(openAiAvailability.audioAnalysisAvailable()).isFalse();
        // SDD T26: the availability response sent to the browser never carries the API key.
        assertThat(openAiAvailability.toString()).doesNotContain(API_KEY);
        assertThat(deepseekAvailability.available()).isTrue();
        assertThat(deepseekAvailability.imageAnalysisAvailable()).isFalse();
        assertThat(disabledAvailability.imageAnalysisAvailable()).isFalse();
    }

    @Test
    void missingApiKeyReturnsFailureWithoutCallingProvider() {
        AiProperties properties = new AiProperties();
        properties.setApiKey(" ");

        AiProviderResult result = client(properties).generateText(prompt(false));

        assertThat(result.success()).isFalse();
        assertThat(result.errorSummary()).isEqualTo("AI API key is missing.");
    }

    @Test
    void defaultsMatchTheProposalProviderAndModel() {
        AiProperties defaults = new AiProperties();

        assertThat(defaults.getProvider()).isEqualTo("openai");
        assertThat(defaults.getApiBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(defaults.getModelName()).isEqualTo(MODEL_NAME);
        assertThat(defaults.isEnableRealProvider()).isFalse();
        // SDD 5.6: 30-second timeout and at most 1,200 output tokens.
        assertThat(defaults.getTimeoutSeconds()).isEqualTo(30);
        assertThat(defaults.getMaxOutputTokens()).isEqualTo(1_200);
    }

    private OpenAiResponsesAiProviderClient client(AiProperties properties) {
        return new OpenAiResponsesAiProviderClient(properties, restTemplateBuilder);
    }

    private static AiProperties properties(HttpServer server) {
        AiProperties properties = new AiProperties();
        properties.setProvider("openai");
        properties.setApiBaseUrl("http://" + LOOPBACK_HOST + ":" + server.getAddress().getPort());
        properties.setApiKey(API_KEY);
        properties.setModelName(MODEL_NAME);
        properties.setTimeoutSeconds(2);
        return properties;
    }

    private static AiProviderPrompt prompt(boolean expectJson) {
        return new AiProviderPrompt(
                "CHAT_TUTOR",
                "Bounded beginner tutor instruction",
                "What does ni hao mean?",
                "en",
                64,
                expectJson,
                List.of(new AiProviderPrompt.AiMessage("assistant", "Previous answer")));
    }

    private static void sendStatus(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
