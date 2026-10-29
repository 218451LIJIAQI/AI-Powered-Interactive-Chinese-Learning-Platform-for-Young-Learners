package com.example.aichinese;

import static com.example.aichinese.support.StubHttpServer.requestBody;
import static com.example.aichinese.support.StubHttpServer.respondJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.example.aichinese.support.StubHttpServer;

/**
 * Unit coverage for {@code OpenAiCompatibleAiProviderClient} -
 * proves the never-throw contract (missing key / model / endpoint
 * surface as failure results rather than exceptions) and the
 * HTTPS-only endpoint integrity check (HTTP only allowed for
 * loopback hosts used by integration tests).
 */
class OpenAiCompatibleAiProviderClientTests {

    private static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";
    private static final String PROVIDER_OPENAI_COMPATIBLE_RESPONSE = "openai-compatible";
    private static final String API_BASE_URL = "https://api.deepseek.com";
    private static final String QUERY_API_BASE_URL = API_BASE_URL + "?api_key=leaky";
    private static final String FRAGMENT_API_BASE_URL = API_BASE_URL + "#fragment";
    private static final String NON_LOOPBACK_HTTP_API_BASE_URL = "http://api.deepseek.com";
    private static final String API_KEY = "test-api-key";
    private static final String MODEL_NAME = "deepseek-chat";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String INVALID_BASE_URL_MESSAGE = "AI API base URL is missing or invalid.";

    private final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();

    static Stream<Arguments> misconfiguredProviders() {
        return Stream.of(
                misconfiguration("missing API key", properties -> properties.setApiKey(" "), "AI API key is missing."),
                misconfiguration("missing model name", properties -> properties.setModelName(" "),
                        "AI model name is missing."),
                misconfiguration("invalid base URL", properties -> properties.setApiBaseUrl("not-a-url"),
                        INVALID_BASE_URL_MESSAGE),
                misconfiguration("base URL with a query", properties -> properties.setApiBaseUrl(QUERY_API_BASE_URL),
                        INVALID_BASE_URL_MESSAGE),
                misconfiguration("base URL with a fragment",
                        properties -> properties.setApiBaseUrl(FRAGMENT_API_BASE_URL), INVALID_BASE_URL_MESSAGE),
                misconfiguration("non-loopback HTTP base URL",
                        properties -> properties.setApiBaseUrl(NON_LOOPBACK_HTTP_API_BASE_URL), INVALID_BASE_URL_MESSAGE));
    }

    /** A misconfiguration is a failure result, found before the API key is sent anywhere. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("misconfiguredProviders")
    void misconfiguredProviderReturnsFailureInsteadOfThrowing(Consumer<AiProperties> misconfiguration, String errorSummary) {
        AiProperties properties = configuredProperties();
        misconfiguration.accept(properties);

        AiProviderResult result = client(properties).generateText(prompt());

        assertProviderFailure(result, errorSummary);
    }

    @Test
    void successfulLoopbackRequestBuildsOpenAiCompatiblePayloadAndIgnoresOversizedTokenCount() throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        try (StubHttpServer server = StubHttpServer.start(CHAT_COMPLETIONS_PATH, exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sentBody.set(requestBody(exchange));
            respondJson(exchange, """
                    {"choices":[{"message":{"content":"你好！"}}],"usage":{"total_tokens":9223372036854775807}}
                    """);
        })) {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(server.baseUrl());
            properties.setProvider(PROVIDER_OPENAI_COMPATIBLE);

            AiProviderResult result = client(properties).generateText(new AiProviderPrompt(
                    "CHAT_TUTOR",
                    "System instruction",
                    "Say hello",
                    64,
                    true,
                    List.of(new AiProviderPrompt.AiMessage("assistant", "Previous answer")),
                    null));

            assertThat(result.success()).isTrue();
            assertThat(result.text()).isEqualTo("你好！");
            assertThat(result.tokenCount()).isNull();
            assertThat(authorizationHeader.get()).isEqualTo("Bearer " + API_KEY);
            assertThat(sentBody.get()).contains(
                    "\"model\":\"" + MODEL_NAME + "\"",
                    "\"role\":\"system\"",
                    "\"role\":\"assistant\"",
                    "\"role\":\"user\"",
                    "\"response_format\":{\"type\":\"json_object\"}");
        }
    }

    @Test
    void emptyProviderTextReturnsFailureInsteadOfThrowing() throws Exception {
        try (StubHttpServer server = StubHttpServer.start(CHAT_COMPLETIONS_PATH,
                exchange -> respondJson(exchange, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"))) {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(server.baseUrl());

            AiProviderResult result = client(properties).generateText(prompt());

            assertProviderFailure(result, "Provider response text was empty.");
        }
    }

    @Test
    void availabilityRejectsUnknownProviderBeforeRuntimeRoutingFallback() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider("unknown-provider");

        AiAvailabilityResponse availability = new AiAvailabilityService(properties).getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.status()).isEqualTo("UNAVAILABLE");
        assertThat(availability.message()).contains("not supported");
        assertGenerationFeaturesUnavailable(availability);
    }

    /** The availability check uses the client's base URL rules: no plain HTTP off loopback, no query or fragment. */
    @ParameterizedTest
    @ValueSource(strings = {NON_LOOPBACK_HTTP_API_BASE_URL, QUERY_API_BASE_URL, FRAGMENT_API_BASE_URL})
    void availabilityUsesSameBaseUrlRulesAsProviderClient(String apiBaseUrl) {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setApiBaseUrl(apiBaseUrl);

        AiAvailabilityResponse availability = new AiAvailabilityService(properties).getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.message()).contains("base URL");
        assertGenerationFeaturesUnavailable(availability);
    }

    @Test
    void availabilityAllowsLoopbackHttpForLocalProviderTestsOnly() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider(PROVIDER_OPENAI_COMPATIBLE);
        properties.setApiBaseUrl("http://localhost:11434");

        AiAvailabilityResponse availability = new AiAvailabilityService(properties).getAvailability();

        assertThat(availability.available()).isTrue();
        assertThat(availability.provider()).isEqualTo(PROVIDER_OPENAI_COMPATIBLE_RESPONSE);
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    @Test
    void aiPropertiesRejectUnsafeTimeoutAndTokenBounds() {
        AiProperties properties = configuredProperties();

        assertThatThrownBy(() -> properties.setTimeoutSeconds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> properties.setMaxOutputTokens(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max output tokens");
        assertThatThrownBy(() -> properties.setMaxOutputTokens(4_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max output tokens");
    }

    private static Arguments misconfiguration(String name, Consumer<AiProperties> change, String errorSummary) {
        return arguments(named(name, change), errorSummary);
    }

    private static AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setApiBaseUrl(API_BASE_URL);
        properties.setApiKey(API_KEY);
        properties.setModelName(MODEL_NAME);
        properties.setTimeoutSeconds(1);
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
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    private static AiProviderPrompt prompt() {
        return new AiProviderPrompt("TEST_FEATURE", "You are a helpful tutor.", "Say hello in Chinese.", 100, false,
                List.of(), null);
    }
}
