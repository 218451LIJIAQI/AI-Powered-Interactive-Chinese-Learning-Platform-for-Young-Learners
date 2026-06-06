package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Unit coverage for {@code OpenAiCompatibleAiProviderClient} -
 * proves the never-throw contract (missing key / model / endpoint
 * surface as failure results rather than exceptions) and the
 * HTTPS-only endpoint integrity check (HTTP only allowed for
 * loopback hosts used by integration tests).
 */
class OpenAiCompatibleAiProviderClientTests {

    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";
    private static final String PROVIDER_OPENAI_COMPATIBLE_RESPONSE = "openai-compatible";
    private static final String UNKNOWN_PROVIDER = "unknown-provider";
    private static final String API_BASE_URL = "https://api.deepseek.com";
    private static final String QUERY_API_BASE_URL = API_BASE_URL + "?api_key=leaky";
    private static final String FRAGMENT_API_BASE_URL = API_BASE_URL + "#fragment";
    private static final String NON_LOOPBACK_HTTP_API_BASE_URL = "http://api.deepseek.com";
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final String LOCAL_PROVIDER_BASE_URL = "http://localhost:11434";
    private static final String API_KEY = "test-api-key";
    private static final String MODEL_NAME = "deepseek-chat";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_API_KEY = "Bearer " + API_KEY;
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String MISSING_API_KEY_MESSAGE = "AI API key is missing.";
    private static final String MISSING_MODEL_MESSAGE = "AI model name is missing.";
    private static final String INVALID_BASE_URL_MESSAGE = "AI API base URL is missing or invalid.";
    private static final String EMPTY_PROVIDER_TEXT_MESSAGE = "Provider response text was empty.";
    private static final String UNAVAILABLE_STATUS = "UNAVAILABLE";
    private static final String BASE_URL_MESSAGE_FRAGMENT = "base URL";
    private static final String UNSUPPORTED_PROVIDER_MESSAGE_FRAGMENT = "not supported";
    private static final String EXPECTED_PROVIDER_TEXT = "你好！";
    private static final int FAST_TIMEOUT_SECONDS = 1;
    private static final int INVALID_TIMEOUT_SECONDS = 0;
    private static final int INVALID_MIN_OUTPUT_TOKENS = 0;
    private static final int INVALID_MAX_OUTPUT_TOKENS = 4_001;
    private static final String OVERSIZED_TOKEN_RESPONSE = """
            {"choices":[{"message":{"content":"你好！"}}],"usage":{"total_tokens":9223372036854775807}}
            """;
    private static final String EMPTY_TEXT_RESPONSE = """
            {"choices":[{"message":{"content":""}}]}
            """;

    private final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();

    @Test
    void missingApiKeyReturnsFailureInsteadOfThrowing() {
        AiProperties properties = configuredProperties();
        properties.setApiKey(" ");

        AiProviderResult result = client(properties).generateText(prompt());

        assertProviderFailure(result, MISSING_API_KEY_MESSAGE);
    }

    @Test
    void missingModelNameReturnsFailureInsteadOfThrowing() {
        AiProperties properties = configuredProperties();
        properties.setModelName(" ");

        AiProviderResult result = client(properties).generateText(prompt());

        assertProviderFailure(result, MISSING_MODEL_MESSAGE);
    }

    @Test
    void invalidApiBaseUrlReturnsFailureInsteadOfThrowing() {
        AiProperties properties = configuredProperties();
        properties.setApiBaseUrl("not-a-url");

        AiProviderResult result = client(properties).generateText(prompt());

        assertProviderFailure(result, INVALID_BASE_URL_MESSAGE);
    }

    @Test
    void apiBaseUrlWithQueryOrFragmentReturnsFailureBeforeSendingApiKey() {
        AiProperties queryProperties = configuredProperties();
        queryProperties.setApiBaseUrl(QUERY_API_BASE_URL);

        AiProperties fragmentProperties = configuredProperties();
        fragmentProperties.setApiBaseUrl(FRAGMENT_API_BASE_URL);
        AiProviderResult queryResult = client(queryProperties).generateText(prompt());
        AiProviderResult fragmentResult = client(fragmentProperties).generateText(prompt());

        assertProviderFailure(queryResult, INVALID_BASE_URL_MESSAGE);
        assertProviderFailure(fragmentResult, INVALID_BASE_URL_MESSAGE);
    }

    @Test
    void nonLoopbackHttpApiBaseUrlReturnsFailureBeforeSendingApiKey() {
        AiProperties properties = configuredProperties();
        properties.setApiBaseUrl(NON_LOOPBACK_HTTP_API_BASE_URL);

        AiProviderResult result = client(properties).generateText(prompt());

        assertProviderFailure(result, INVALID_BASE_URL_MESSAGE);
    }

    @Test
    void successfulLoopbackRequestBuildsOpenAiCompatiblePayloadAndIgnoresOversizedTokenCount() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext(CHAT_COMPLETIONS_PATH, exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst(AUTHORIZATION_HEADER));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, OVERSIZED_TOKEN_RESPONSE);
        });
        server.start();
        try {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(loopbackBaseUrl(server));
            properties.setProvider(PROVIDER_OPENAI_COMPATIBLE);

            AiProviderResult result = client(properties).generateText(chatTutorPrompt());

            assertThat(result.success()).isTrue();
            assertThat(result.providerName()).isEqualTo(PROVIDER_OPENAI_COMPATIBLE_RESPONSE);
            assertThat(result.text()).isEqualTo(EXPECTED_PROVIDER_TEXT);
            assertThat(result.tokenCount()).isNull();
            assertThat(authorizationHeader.get()).isEqualTo(BEARER_API_KEY);
            assertThat(requestBody.get()).contains("\"model\":\"" + MODEL_NAME + "\"");
            assertThat(requestBody.get()).contains("\"role\":\"system\"");
            assertThat(requestBody.get()).contains("\"role\":\"assistant\"");
            assertThat(requestBody.get()).contains("\"role\":\"user\"");
            assertThat(requestBody.get()).contains("\"response_format\":{\"type\":\"json_object\"}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void malformedProviderResponseReturnsFailureInsteadOfThrowing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        server.createContext(CHAT_COMPLETIONS_PATH, exchange -> sendJson(exchange, EMPTY_TEXT_RESPONSE));
        server.start();
        try {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(loopbackBaseUrl(server));

            AiProviderResult result = client(properties).generateText(prompt());

            assertProviderFailure(result, EMPTY_PROVIDER_TEXT_MESSAGE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void availabilityRejectsUnknownProviderBeforeRuntimeRoutingFallback() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider(UNKNOWN_PROVIDER);
        AiAvailabilityService availabilityService = new AiAvailabilityService(properties);

        var availability = availabilityService.getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.status()).isEqualTo(UNAVAILABLE_STATUS);
        assertThat(availability.message()).contains(UNSUPPORTED_PROVIDER_MESSAGE_FRAGMENT);
        assertGenerationFeaturesUnavailable(availability);
    }

    @Test
    void availabilityUsesSameHttpBaseUrlRulesAsProviderClient() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setApiBaseUrl(NON_LOOPBACK_HTTP_API_BASE_URL);
        AiAvailabilityService availabilityService = new AiAvailabilityService(properties);

        var availability = availabilityService.getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.message()).contains(BASE_URL_MESSAGE_FRAGMENT);
        assertGenerationFeaturesUnavailable(availability);
    }

    @Test
    void availabilityRejectsBaseUrlQueryAndFragment() {
        AiProperties queryProperties = configuredProperties();
        queryProperties.setEnableRealProvider(true);
        queryProperties.setApiBaseUrl(QUERY_API_BASE_URL);
        AiAvailabilityService queryAvailability = new AiAvailabilityService(queryProperties);

        AiProperties fragmentProperties = configuredProperties();
        fragmentProperties.setEnableRealProvider(true);
        fragmentProperties.setApiBaseUrl(FRAGMENT_API_BASE_URL);
        AiAvailabilityService fragmentAvailability = new AiAvailabilityService(fragmentProperties);
        var queryResult = queryAvailability.getAvailability();
        var fragmentResult = fragmentAvailability.getAvailability();

        assertThat(queryResult.available()).isFalse();
        assertThat(queryResult.message()).contains(BASE_URL_MESSAGE_FRAGMENT);
        assertThat(fragmentResult.available()).isFalse();
        assertThat(fragmentResult.message()).contains(BASE_URL_MESSAGE_FRAGMENT);
    }

    @Test
    void availabilityAllowsLoopbackHttpForLocalProviderTestsOnly() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider(PROVIDER_OPENAI_COMPATIBLE);
        properties.setApiBaseUrl(LOCAL_PROVIDER_BASE_URL);
        AiAvailabilityService availabilityService = new AiAvailabilityService(properties);

        var availability = availabilityService.getAvailability();

        assertThat(availability.available()).isTrue();
        assertThat(availability.provider()).isEqualTo(PROVIDER_OPENAI_COMPATIBLE_RESPONSE);
        assertThat(availability.textGenerationAvailable()).isTrue();
        assertThat(availability.audioAnalysisAvailable()).isFalse();
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    @Test
    void aiPropertiesRejectUnsafeTimeoutAndTokenBounds() {
        AiProperties properties = configuredProperties();

        assertThatThrownBy(() -> properties.setTimeoutSeconds(INVALID_TIMEOUT_SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> properties.setMaxOutputTokens(INVALID_MIN_OUTPUT_TOKENS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max output tokens");
        assertThatThrownBy(() -> properties.setMaxOutputTokens(INVALID_MAX_OUTPUT_TOKENS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max output tokens");
    }

    private AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setProvider(PROVIDER_DEEPSEEK);
        properties.setApiBaseUrl(API_BASE_URL);
        properties.setApiKey(API_KEY);
        properties.setModelName(MODEL_NAME);
        properties.setTimeoutSeconds(FAST_TIMEOUT_SECONDS);
        return properties;
    }

    private OpenAiCompatibleAiProviderClient client(AiProperties properties) {
        return new OpenAiCompatibleAiProviderClient(properties, restTemplateBuilder);
    }

    private static void assertProviderFailure(AiProviderResult result, String errorSummary) {
        assertThat(result.success()).isFalse();
        assertThat(result.errorSummary()).isEqualTo(errorSummary);
    }

    private static void assertGenerationFeaturesUnavailable(AiAvailabilityResponse availability) {
        assertThat(availability.textGenerationAvailable()).isFalse();
        assertThat(availability.audioAnalysisAvailable()).isFalse();
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    private AiProviderPrompt prompt() {
        return new AiProviderPrompt(
                "TEST_FEATURE",
                "You are a helpful tutor.",
                "Say hello in Chinese.",
                "en",
                100,
                false,
                List.of());
    }

    private AiProviderPrompt chatTutorPrompt() {
        return new AiProviderPrompt(
                "CHAT_TUTOR",
                "System instruction",
                "Say hello",
                "en",
                64,
                true,
                List.of(new AiProviderPrompt.AiMessage("assistant", "Previous answer")));
    }

    private String loopbackBaseUrl(HttpServer server) {
        return "http://" + LOOPBACK_HOST + ":" + server.getAddress().getPort();
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
