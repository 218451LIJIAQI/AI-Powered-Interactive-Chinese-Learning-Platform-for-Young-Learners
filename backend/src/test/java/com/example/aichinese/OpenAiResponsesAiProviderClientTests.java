package com.example.aichinese;

import static com.example.aichinese.support.StubHttpServer.requestBody;
import static com.example.aichinese.support.StubHttpServer.respond;
import static com.example.aichinese.support.StubHttpServer.respondJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.OpenAiResponsesAiProviderClient;
import com.example.aichinese.support.StubHttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;

/**
 * Unit coverage for {@code OpenAiResponsesAiProviderClient}, the default provider named in the
 * proposal (OpenAI Responses API, GPT-5.6 Luna): request shape, response parsing and the
 * never-throw failure contract (Proposal Table 6.2, AI-integration testing).
 */
class OpenAiResponsesAiProviderClientTests {

    private static final String RESPONSES_PATH = "/responses";
    private static final String API_KEY = "test-api-key";
    private static final String MODEL_NAME = "gpt-5.6-luna";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String COMPLETED_RESPONSE = """
            {"id":"resp_1","status":"completed","output":[
              {"type":"reasoning","summary":[]},
              {"type":"message","role":"assistant","content":[
                {"type":"output_text","text":"你好 (nǐ hǎo) means hello."}]}],
             "usage":{"input_tokens":20,"output_tokens":12,"total_tokens":32}}
            """;

    private final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completedResponseBuildsResponsesPayloadAndExtractsOutputText(boolean expectJson) throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sentBody.set(requestBody(exchange));
            respondJson(exchange, COMPLETED_RESPONSE);
        })) {
            AiProviderResult result = client(properties(server)).generateText(prompt(expectJson));

            assertThat(result.success()).isTrue();
            assertThat(result.text()).isEqualTo("你好 (nǐ hǎo) means hello.");
            assertThat(result.tokenCount()).isEqualTo(32);
            assertThat(authorization.get()).isEqualTo("Bearer " + API_KEY);
            JsonNode body = JSON.readTree(sentBody.get());
            assertThat(body.path("model").textValue()).isEqualTo(MODEL_NAME);
            assertThat(body.path("instructions").textValue()).isEqualTo("Bounded beginner tutor instruction");
            assertThat(body.path("max_output_tokens").numberValue()).isEqualTo(64);
            assertThat(body.path("store")).isEqualTo(BooleanNode.FALSE);
            var expectedInput = JSON.createArrayNode();
            if (expectJson) {
                expectedInput.addObject().put("role", "developer").put("content", "Return a JSON object.");
                assertThat(body.at("/text/format/type").asText()).isEqualTo("json_object");
            } else {
                assertThat(body.has("text")).isFalse();
            }
            expectedInput.addObject().put("role", "assistant").put("content", "Previous answer");
            expectedInput.addObject().put("role", "user").put("content", "What does ni hao mean?");
            assertThat(body.path("input")).isEqualTo(expectedInput);
            assertThat(body.has("messages")).isFalse();
        }
    }

    /**
     * An incomplete reply (here a JSON reply cut off at the token limit) is rejected instead of returning
     * truncated JSON, and a failed provider status is a controlled failure.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            true  | {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[{"type":"message","content":[{"type":"output_text","text":"{\\"word\\":"}]}]} | Provider response was incomplete.
            false | {"status":"failed","error":{"code":"server_error","message":"internal"},"output":[]}                                                                          | Provider response status was not completed.
            """)
    void unfinishedProviderResponseIsAControlledFailure(boolean expectJson, String response, String errorSummary)
            throws Exception {
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> respondJson(exchange, response))) {
            AiProviderResult result = client(properties(server)).generateText(prompt(expectJson));

            assertThat(result.success()).isFalse();
            assertThat(result.errorSummary()).isEqualTo(errorSummary);
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void slowProviderTimesOutWithAControlledFailureWithinTheConfiguredTimeout() throws Exception {
        // Proposal Table 6.2 "AI - timeouts": the stub never answers so the client must give up at its 1 s timeout.
        CountDownLatch hang = new CountDownLatch(1);
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
            try {
                hang.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        })) {
            AiProperties properties = properties(server);
            properties.setTimeoutSeconds(1);
            long started = System.nanoTime();

            AiProviderResult result = client(properties).generateText(prompt(false));

            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            assertThat(result.success()).isFalse();
            assertThat(result.text()).isNull();
            assertThat(result.errorSummary()).isEqualTo("Provider request failed or timed out.");
            // A connection failure has the same summary, so the lower bound shows the call waited for the timeout.
            assertThat(elapsedMillis).as("the call waits for the configured 1 s timeout").isBetween(900L, 5_000L);
        } finally {
            hang.countDown();
        }
    }

    /**
     * A reply that trickles in (each piece well within the timeout) must still end at the configured deadline: the
     * chat stream and the browser stop waiting soon after it. {@code HttpAiProviderClientTests} covers the deadline itself.
     */
    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void aReplyThatTricklesInIsCutOffAtTheTotalDeadline() throws Exception {
        AtomicInteger chunksSent = new AtomicInteger();
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
            byte[] filler = " ".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try (exchange) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                chunksSent.incrementAndGet();
                for (int i = 0; i < 50 && !Thread.currentThread().isInterrupted(); i++) {
                    Thread.sleep(200L);
                    exchange.getResponseBody().write(filler);
                    exchange.getResponseBody().flush();
                    chunksSent.incrementAndGet();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client has already given up.
            }
        })) {
            AiProperties properties = properties(server);
            properties.setTimeoutSeconds(1);
            long started = System.nanoTime();

            AiProviderResult result = client(properties).generateText(prompt(false));

            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            assertThat(result.success()).isFalse();
            assertThat(result.errorSummary()).isEqualTo("Provider request failed or timed out.");
            assertThat(chunksSent.get()).as("server actually started trickling chunks").isGreaterThanOrEqualTo(2);
            assertThat(elapsedMillis).as("waits until the 1 s deadline rather than failing immediately or hanging")
                    .isBetween(900L, 5_000L);
        }
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {500, 503, 429, 401})
    void errorStatusesBecomeControlledFailuresThatNameTheStatus(int statusCode) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
            calls.incrementAndGet();
            // Like OpenAI's own errors, the body may echo the key it was sent.
            respond(exchange, statusCode, "{\"error\":{\"message\":\"Provider unavailable for key " + API_KEY
                    + "\",\"code\":\"" + statusCode + "\"}}");
        })) {
            AiProperties properties = properties(server);
            properties.setTimeoutSeconds(3);

            AiProviderResult result = client(properties).generateText(prompt(true));

            assertThat(calls.get()).as("calls to the provider stub").isEqualTo(1);
            assertThat(result.success()).isFalse();
            assertThat(result.text()).isNull();
            // The admin's usage log names the status (a wrong key, the provider's own limit), never the body.
            assertThat(result.errorSummary()).isEqualTo("Provider answered with HTTP " + statusCode + ".");
        }
    }

    @Test
    void imagePromptIsSentAsInputTextPlusInputImageDataUrl() throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
            sentBody.set(requestBody(exchange));
            respondJson(exchange, COMPLETED_RESPONSE);
        })) {
            byte[] imageBytes = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
            AiProviderPrompt prompt = new AiProviderPrompt(
                    "WRITING_FEEDBACK",
                    "Bounded handwriting instruction",
                    "Give short handwriting practice feedback in JSON.",
                    true,
                    List.of(),
                    new AiProviderPrompt.AiImageInput(imageBytes, "image/png"));

            AiProviderResult result = client(properties(server)).generateText(prompt);

            assertThat(result.success()).isTrue();
            String expectedDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
            JsonNode body = JSON.readTree(sentBody.get());
            assertThat(body.at("/input/0/role").asText()).isEqualTo("developer");
            assertThat(body.at("/input/0/content").asText()).contains("JSON");
            assertThat(body.at("/input/1/role").asText()).isEqualTo("user");
            JsonNode content = body.at("/input/1/content");
            assertThat(body.path("store")).isEqualTo(BooleanNode.FALSE);
            assertThat(content.isArray()).isTrue();
            assertThat(content.get(0).path("type").asText()).isEqualTo("input_text");
            assertThat(content.get(0).path("text").asText()).isEqualTo("Give short handwriting practice feedback in JSON.");
            assertThat(content.get(1).path("type").asText()).isEqualTo("input_image");
            assertThat(content.get(1).path("image_url").asText()).isEqualTo(expectedDataUrl);
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

        AiAvailabilityService.Availability openAiAvailability = new AiAvailabilityService(openAi).getAvailability();
        AiAvailabilityService.Availability deepseekAvailability = new AiAvailabilityService(deepseek).getAvailability();
        AiAvailabilityService.Availability disabledAvailability = new AiAvailabilityService(disabledOpenAi).getAvailability();

        assertThat(openAiAvailability.available()).isTrue();
        assertThat(openAiAvailability.imageAnalysisAvailable()).isTrue();
        // SDD T26: what the server knows about availability never carries the API key.
        assertThat(openAiAvailability.toString()).doesNotContain(API_KEY);
        assertThat(deepseekAvailability.available()).isTrue();
        assertThat(deepseekAvailability.imageAnalysisAvailable()).isFalse();
        assertThat(disabledAvailability.imageAnalysisAvailable()).isFalse();
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

    private static AiProperties properties(StubHttpServer server) {
        AiProperties properties = new AiProperties();
        properties.setProvider("openai");
        properties.setApiBaseUrl(server.baseUrl());
        properties.setApiKey(API_KEY);
        properties.setModelName(MODEL_NAME);
        properties.setTimeoutSeconds(2);
        properties.setMaxOutputTokens(64);
        return properties;
    }

    private static AiProviderPrompt prompt(boolean expectJson) {
        return new AiProviderPrompt(
                "CHAT_TUTOR",
                "Bounded beginner tutor instruction",
                "What does ni hao mean?",
                expectJson,
                List.of(new AiProviderPrompt.AiMessage("assistant", "Previous answer")),
                null);
    }
}
