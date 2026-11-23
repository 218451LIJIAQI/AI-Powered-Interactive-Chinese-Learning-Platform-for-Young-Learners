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
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.example.aichinese.support.StubHttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit coverage for {@code OpenAiCompatibleAiProviderClient} -
 * proves the never-throw contract (missing key / model / endpoint
 * surface as failure results rather than exceptions) and the
 * HTTPS-only endpoint integrity check (HTTP only allowed for
 * loopback hosts used by integration tests).
 */
class OpenAiCompatibleAiProviderClientTests {

    private static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
    private static final String API_BASE_URL = "https://api.deepseek.com";
    private static final String QUERY_API_BASE_URL = API_BASE_URL + "?api_key=leaky";
    private static final String FRAGMENT_API_BASE_URL = API_BASE_URL + "#fragment";
    private static final String NON_LOOPBACK_HTTP_API_BASE_URL = "http://api.deepseek.com";
    private static final String API_KEY = "test-api-key";
    private static final String MODEL_NAME = "deepseek-chat";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();

    static Stream<Arguments> misconfiguredProviders() {
        return Stream.of(
                misconfiguration("missing API key", properties -> properties.setApiKey(" ")),
                misconfiguration("missing model name", properties -> properties.setModelName(" ")),
                misconfiguration("invalid base URL", properties -> properties.setApiBaseUrl("not-a-url")),
                misconfiguration("base URL with a query", properties -> properties.setApiBaseUrl(QUERY_API_BASE_URL)),
                misconfiguration("base URL with a fragment", properties -> properties.setApiBaseUrl(FRAGMENT_API_BASE_URL)),
                misconfiguration("non-loopback HTTP base URL",
                        properties -> properties.setApiBaseUrl(NON_LOOPBACK_HTTP_API_BASE_URL)));
    }

    /** A misconfiguration is a failure result, found before the API key is sent anywhere. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("misconfiguredProviders")
    void misconfiguredProviderReturnsFailureInsteadOfThrowing(Consumer<AiProperties> misconfiguration) {
        AiProperties properties = configuredProperties();
        misconfiguration.accept(properties);

        AiProviderResult result = client(properties).generateText(prompt());

        assertProviderFailure(result, "AI provider is not configured.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775807", "3.5"})
    void successfulLoopbackRequestBuildsOpenAiCompatiblePayloadAndIgnoresInvalidTokenCount(String tokenCount)
            throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        try (StubHttpServer server = StubHttpServer.start(CHAT_COMPLETIONS_PATH, exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sentBody.set(requestBody(exchange));
            respondJson(exchange, """
                    {"choices":[{"message":{"content":"你好！"}}],"usage":{"total_tokens":%s}}
                    """.formatted(tokenCount));
        })) {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(server.baseUrl());
            properties.setProvider(PROVIDER_OPENAI_COMPATIBLE);

            AiProviderResult result = client(properties).generateText(new AiProviderPrompt(
                    "CHAT_TUTOR",
                    "System instruction",
                    "Say hello",
                    true,
                    List.of(new AiProviderPrompt.AiMessage("assistant", "Previous answer")),
                    null));

            assertThat(result.success()).isTrue();
            assertThat(result.text()).isEqualTo("你好！");
            assertThat(result.tokenCount()).isNull();
            assertThat(authorizationHeader.get()).isEqualTo("Bearer " + API_KEY);
            ObjectMapper json = new ObjectMapper();
            JsonNode body = json.readTree(sentBody.get());
            assertThat(body.path("model").textValue()).isEqualTo(MODEL_NAME);
            assertThat(body.path("messages")).isEqualTo(json.readTree("""
                    [{"role":"system","content":"System instruction"},
                     {"role":"assistant","content":"Previous answer"},
                     {"role":"user","content":"Say hello"}]
                    """));
            assertThat(body.at("/response_format/type").textValue()).isEqualTo("json_object");
        }
    }

    @Test
    void emptyProviderTextReturnsFailureInsteadOfThrowing() throws Exception {
        try (StubHttpServer server = StubHttpServer.start(CHAT_COMPLETIONS_PATH,
                exchange -> respondJson(exchange, "{\"choices\":[{\"message\":{\"content\":\"\"}}],\"usage\":{\"total_tokens\":17}}"))) {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(server.baseUrl());

            AiProviderResult result = client(properties).generateText(prompt());

            assertProviderFailure(result, "Provider response text was empty.");
            assertThat(result.tokenCount()).isEqualTo(17);
        }
    }

    @Test
    void filteredOrTruncatedJsonFinishReasonsReturnFailureWithTokenCount() throws Exception {
        try (StubHttpServer server = StubHttpServer.start(CHAT_COMPLETIONS_PATH,
                exchange -> respondJson(exchange,
                        "{\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":\"partial\"}}],\"usage\":{\"total_tokens\":21}}"))) {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(server.baseUrl());

            AiProviderResult result = client(properties).generateText(prompt());

            assertProviderFailure(result, "Provider response was filtered.");
            assertThat(result.tokenCount()).isEqualTo(21);
        }

        try (StubHttpServer server = StubHttpServer.start(CHAT_COMPLETIONS_PATH,
                exchange -> respondJson(exchange,
                        "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\\\"title\\\":\\\"trun\"}}],\"usage\":{\"total_tokens\":99}}"))) {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(server.baseUrl());
            AiProviderPrompt jsonPrompt = new AiProviderPrompt("QUIZ_GENERATOR", "System", "User", true, List.of(), null);

            AiProviderResult result = client(properties).generateText(jsonPrompt);

            assertProviderFailure(result, "Provider response was incomplete.");
            assertThat(result.tokenCount()).isEqualTo(99);
        }
    }

    @Test
    void availabilityRejectsUnknownProviderBeforeRuntimeRoutingFallback() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider("unknown-provider");

        AiAvailabilityService.Availability availability = new AiAvailabilityService(properties).getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.message()).contains("not supported");
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    /** The status endpoint only advertises an AI provider with a usable base URL. */
    @ParameterizedTest
    @ValueSource(strings = {NON_LOOPBACK_HTTP_API_BASE_URL, QUERY_API_BASE_URL, FRAGMENT_API_BASE_URL,
            "https://api.example.test:65536/v1"})
    void availabilityUsesSameBaseUrlRulesAsProviderClient(String apiBaseUrl) {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setApiBaseUrl(apiBaseUrl);

        AiAvailabilityService.Availability availability = new AiAvailabilityService(properties).getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.message()).contains("base URL");
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    @Test
    void availabilityAllowsLoopbackHttpForLocalProviderTestsOnly() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider(PROVIDER_OPENAI_COMPATIBLE);
        properties.setApiBaseUrl("http://localhost:11434");

        AiAvailabilityService.Availability availability = new AiAvailabilityService(properties).getAvailability();

        assertThat(availability.available()).isTrue();
        assertThat(availability.provider()).isEqualTo(PROVIDER_OPENAI_COMPATIBLE);
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    @Test
    void aiPropertiesRejectUnsafeTimeoutAndTokenBounds() {
        AiProperties properties = configuredProperties();

        assertThatThrownBy(() -> properties.setTimeoutSeconds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        // Leave time between the provider deadline, the 45 s chat stream timeout and the 50 s browser timeout.
        assertThatThrownBy(() -> properties.setTimeoutSeconds(41))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI timeout must be between 1 and 40 seconds.");
        properties.setTimeoutSeconds(AiProperties.MAX_TIMEOUT_SECONDS);
        assertThat(properties.getTimeoutSeconds()).isEqualTo(40);
        assertThatThrownBy(() -> properties.setMaxOutputTokens(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max output tokens");
        assertThatThrownBy(() -> properties.setMaxOutputTokens(4_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max output tokens");
    }

    private static Arguments misconfiguration(String name, Consumer<AiProperties> change) {
        return arguments(named(name, change));
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

    private static AiProviderPrompt prompt() {
        return new AiProviderPrompt("TEST_FEATURE", "You are a helpful tutor.", "Say hello in Chinese.", false,
                List.of(), null);
    }
}
