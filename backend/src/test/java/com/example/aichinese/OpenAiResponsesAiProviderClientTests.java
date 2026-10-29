package com.example.aichinese;

import static com.example.aichinese.support.StubHttpServer.requestBody;
import static com.example.aichinese.support.StubHttpServer.respond;
import static com.example.aichinese.support.StubHttpServer.respondJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.OpenAiResponsesAiProviderClient;
import com.example.aichinese.support.StubHttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit coverage for {@code OpenAiResponsesAiProviderClient}, the default provider named in the
 * proposal (OpenAI Responses API, GPT-5.6 Luna): request shape, response parsing and the
 * never-throw failure contract (Proposal Table 6.2, AI-integration testing).
 */
class OpenAiResponsesAiProviderClientTests {

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

    private final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();

    @Test
    void completedResponseBuildsResponsesPayloadAndExtractsOutputText() throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sentBody.set(requestBody(exchange));
            respondJson(exchange, COMPLETED_RESPONSE);
        })) {
            AiProviderResult result = client(properties(server)).generateText(prompt(true));

            assertThat(result.success()).isTrue();
            assertThat(result.text()).isEqualTo("你好 (nǐ hǎo) means hello.");
            assertThat(result.tokenCount()).isEqualTo(32);
            assertThat(authorization.get()).isEqualTo("Bearer " + API_KEY);
            assertThat(sentBody.get())
                    .contains("\"model\":\"" + MODEL_NAME + "\"")
                    .contains("\"instructions\":\"Bounded beginner tutor instruction\"")
                    .contains("\"max_output_tokens\":64")
                    .contains("\"store\":false")
                    .contains("\"role\":\"assistant\"")
                    .contains("\"text\":{\"format\":{\"type\":\"json_object\"}}")
                    .doesNotContain("\"messages\"");
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
    void slowProviderTimesOutWithAControlledFailureWithinTheConfiguredTimeout() throws Exception {
        // Proposal Table 6.2 "AI - timeouts": the stub answers after the 1 s client timeout.
        try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
            try {
                Thread.sleep(2_500L);
                respondJson(exchange, COMPLETED_RESPONSE);
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
            assertThat(result.text()).isNull();
            assertThat(result.errorSummary()).isNotBlank();
            // Every failure has the same summary, so the timing shows that the timeout (not an early error) ended it.
            assertThat(elapsedMillis).as("the call waits for the timeout").isGreaterThanOrEqualTo(900L);
            assertThat(elapsedMillis).as("the call is bounded by the timeout").isLessThan(2_400L);
        }
    }

    @Test
    void serverErrorAndRateLimitResponsesBecomeControlledFailures() throws Exception {
        for (int statusCode : new int[] {500, 503, 429}) {
            AtomicInteger calls = new AtomicInteger();
            try (StubHttpServer server = StubHttpServer.start(RESPONSES_PATH, exchange -> {
                calls.incrementAndGet();
                respond(exchange, statusCode,
                        "{\"error\":{\"message\":\"provider unavailable\",\"code\":\"" + statusCode + "\"}}");
            })) {
                AiProperties properties = properties(server);
                properties.setTimeoutSeconds(3);
                long started = System.nanoTime();

                AiProviderResult result = client(properties).generateText(prompt(true));

                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                assertThat(calls.get()).as("HTTP " + statusCode + " reached the provider stub once").isEqualTo(1);
                // A timeout gives the same summary, so the timing shows that the status itself ended the call.
                assertThat(elapsedMillis).as("HTTP " + statusCode + " fails fast instead of timing out")
                        .isLessThan(1_500L);
                assertThat(result.success()).as("HTTP " + statusCode + " is not a success").isFalse();
                assertThat(result.text()).isNull();
                assertThat(result.errorSummary()).as("HTTP " + statusCode + " has a safe error summary").isNotBlank();
                assertThat(result.errorSummary()).doesNotContain(API_KEY);
            }
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
                    64,
                    true,
                    List.of(),
                    new AiProviderPrompt.AiImageInput(imageBytes, "image/png"));

            AiProviderResult result = client(properties(server)).generateText(prompt);

            assertThat(result.success()).isTrue();
            String expectedDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
            JsonNode body = new ObjectMapper().readTree(sentBody.get());
            JsonNode content = body.path("input").get(0).path("content");
            assertThat(body.path("store").asBoolean(true)).isFalse();
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

        AiAvailabilityResponse openAiAvailability = new AiAvailabilityService(openAi).getAvailability();
        AiAvailabilityResponse deepseekAvailability = new AiAvailabilityService(deepseek).getAvailability();
        AiAvailabilityResponse disabledAvailability = new AiAvailabilityService(disabledOpenAi).getAvailability();

        assertThat(openAiAvailability.available()).isTrue();
        assertThat(openAiAvailability.imageAnalysisAvailable()).isTrue();
        // SDD T26: the availability response sent to the browser never carries the API key.
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
        return properties;
    }

    private static AiProviderPrompt prompt(boolean expectJson) {
        return new AiProviderPrompt(
                "CHAT_TUTOR",
                "Bounded beginner tutor instruction",
                "What does ni hao mean?",
                64,
                expectJson,
                List.of(new AiProviderPrompt.AiMessage("assistant", "Previous answer")),
                null);
    }
}
