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
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Unit coverage for {@code OpenAiCompatibleAiProviderClient} —
 * proves the never-throw contract (missing key / model / endpoint
 * surface as failure results rather than exceptions) and the
 * HTTPS-only endpoint integrity check (HTTP only allowed for
 * loopback hosts used by integration tests).
 */
class OpenAiCompatibleAiProviderClientTests {

    @Test
    void missingApiKeyReturnsFailureInsteadOfThrowing() {
        AiProperties properties = configuredProperties();
        properties.setApiKey(" ");
        OpenAiCompatibleAiProviderClient client = new OpenAiCompatibleAiProviderClient(properties, new RestTemplateBuilder());

        AiProviderResult result = client.generateText(prompt());

        assertThat(result.success()).isFalse();
        assertThat(result.errorSummary()).isEqualTo("AI API key is missing.");
    }

    @Test
    void missingModelNameReturnsFailureInsteadOfThrowing() {
        AiProperties properties = configuredProperties();
        properties.setModelName(" ");
        OpenAiCompatibleAiProviderClient client = new OpenAiCompatibleAiProviderClient(properties, new RestTemplateBuilder());

        AiProviderResult result = client.generateText(prompt());

        assertThat(result.success()).isFalse();
        assertThat(result.errorSummary()).isEqualTo("AI model name is missing.");
    }

    @Test
    void invalidApiBaseUrlReturnsFailureInsteadOfThrowing() {
        AiProperties properties = configuredProperties();
        properties.setApiBaseUrl("not-a-url");
        OpenAiCompatibleAiProviderClient client = new OpenAiCompatibleAiProviderClient(properties, new RestTemplateBuilder());

        AiProviderResult result = client.generateText(prompt());

        assertThat(result.success()).isFalse();
        assertThat(result.errorSummary()).isEqualTo("AI API base URL is missing or invalid.");
    }

    @Test
    void apiBaseUrlWithQueryOrFragmentReturnsFailureBeforeSendingApiKey() {
        AiProperties queryProperties = configuredProperties();
        queryProperties.setApiBaseUrl("https://api.deepseek.com?api_key=leaky");
        OpenAiCompatibleAiProviderClient queryClient = new OpenAiCompatibleAiProviderClient(queryProperties, new RestTemplateBuilder());

        AiProperties fragmentProperties = configuredProperties();
        fragmentProperties.setApiBaseUrl("https://api.deepseek.com#fragment");
        OpenAiCompatibleAiProviderClient fragmentClient = new OpenAiCompatibleAiProviderClient(fragmentProperties, new RestTemplateBuilder());
        AiProviderResult queryResult = queryClient.generateText(prompt());
        AiProviderResult fragmentResult = fragmentClient.generateText(prompt());

        assertThat(queryResult.success()).isFalse();
        assertThat(queryResult.errorSummary()).isEqualTo("AI API base URL is missing or invalid.");
        assertThat(fragmentResult.success()).isFalse();
        assertThat(fragmentResult.errorSummary()).isEqualTo("AI API base URL is missing or invalid.");
    }

    @Test
    void nonLoopbackHttpApiBaseUrlReturnsFailureBeforeSendingApiKey() {
        AiProperties properties = configuredProperties();
        properties.setApiBaseUrl("http://api.deepseek.com");
        OpenAiCompatibleAiProviderClient client = new OpenAiCompatibleAiProviderClient(properties, new RestTemplateBuilder());

        AiProviderResult result = client.generateText(prompt());

        assertThat(result.success()).isFalse();
        assertThat(result.errorSummary()).isEqualTo("AI API base URL is missing or invalid.");
    }

    @Test
    void successfulLoopbackRequestBuildsOpenAiCompatiblePayloadAndIgnoresOversizedTokenCount() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, """
                    {"choices":[{"message":{"content":"你好！"}}],"usage":{"total_tokens":9223372036854775807}}
                    """);
        });
        server.start();
        try {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(loopbackBaseUrl(server));
            properties.setProvider("openai_compatible");
            OpenAiCompatibleAiProviderClient client = new OpenAiCompatibleAiProviderClient(properties, new RestTemplateBuilder());

            AiProviderResult result = client.generateText(new AiProviderPrompt(
                    "CHAT_TUTOR",
                    "System instruction",
                    "Say hello",
                    "en",
                    64,
                    true,
                    List.of(new AiProviderPrompt.AiMessage("assistant", "Previous answer"))));

            assertThat(result.success()).isTrue();
            assertThat(result.providerName()).isEqualTo("openai-compatible");
            assertThat(result.text()).isEqualTo("你好！");
            assertThat(result.tokenCount()).isNull();
            assertThat(authorizationHeader.get()).isEqualTo("Bearer test-api-key");
            assertThat(requestBody.get()).contains("\"model\":\"deepseek-chat\"");
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
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> sendJson(exchange, """
                {"choices":[{"message":{"content":""}}]}
                """));
        server.start();
        try {
            AiProperties properties = configuredProperties();
            properties.setApiBaseUrl(loopbackBaseUrl(server));
            OpenAiCompatibleAiProviderClient client = new OpenAiCompatibleAiProviderClient(properties, new RestTemplateBuilder());

            AiProviderResult result = client.generateText(prompt());

            assertThat(result.success()).isFalse();
            assertThat(result.errorSummary()).isEqualTo("Provider response text was empty.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void availabilityRejectsUnknownProviderBeforeRuntimeRoutingFallback() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider("unknown-provider");
        AiAvailabilityService availabilityService = new AiAvailabilityService(properties);

        var availability = availabilityService.getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.status()).isEqualTo("UNAVAILABLE");
        assertThat(availability.message()).contains("not supported");
        assertThat(availability.textGenerationAvailable()).isFalse();
        assertThat(availability.audioAnalysisAvailable()).isFalse();
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    @Test
    void availabilityUsesSameHttpBaseUrlRulesAsProviderClient() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setApiBaseUrl("http://api.deepseek.com");
        AiAvailabilityService availabilityService = new AiAvailabilityService(properties);

        var availability = availabilityService.getAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.message()).contains("base URL");
        assertThat(availability.textGenerationAvailable()).isFalse();
        assertThat(availability.audioAnalysisAvailable()).isFalse();
        assertThat(availability.imageAnalysisAvailable()).isFalse();
    }

    @Test
    void availabilityRejectsBaseUrlQueryAndFragment() {
        AiProperties queryProperties = configuredProperties();
        queryProperties.setEnableRealProvider(true);
        queryProperties.setApiBaseUrl("https://api.deepseek.com?api_key=leaky");
        AiAvailabilityService queryAvailability = new AiAvailabilityService(queryProperties);

        AiProperties fragmentProperties = configuredProperties();
        fragmentProperties.setEnableRealProvider(true);
        fragmentProperties.setApiBaseUrl("https://api.deepseek.com#fragment");
        AiAvailabilityService fragmentAvailability = new AiAvailabilityService(fragmentProperties);
        var queryResult = queryAvailability.getAvailability();
        var fragmentResult = fragmentAvailability.getAvailability();

        assertThat(queryResult.available()).isFalse();
        assertThat(queryResult.message()).contains("base URL");
        assertThat(fragmentResult.available()).isFalse();
        assertThat(fragmentResult.message()).contains("base URL");
    }

    @Test
    void availabilityAllowsLoopbackHttpForLocalProviderTestsOnly() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider("openai_compatible");
        properties.setApiBaseUrl("http://localhost:11434");
        AiAvailabilityService availabilityService = new AiAvailabilityService(properties);

        var availability = availabilityService.getAvailability();

        assertThat(availability.available()).isTrue();
        assertThat(availability.provider()).isEqualTo("openai-compatible");
        assertThat(availability.textGenerationAvailable()).isTrue();
        assertThat(availability.audioAnalysisAvailable()).isFalse();
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
        assertThatThrownBy(() -> properties.setMaxOutputTokens(4001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max output tokens");
    }

    private AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setApiBaseUrl("https://api.deepseek.com");
        properties.setApiKey("test-api-key");
        properties.setModelName("deepseek-chat");
        properties.setTimeoutSeconds(1);
        return properties;
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

    private String loopbackBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
