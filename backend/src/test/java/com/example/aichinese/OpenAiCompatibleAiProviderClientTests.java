package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;

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
    void nonLoopbackHttpApiBaseUrlReturnsFailureBeforeSendingApiKey() {
        AiProperties properties = configuredProperties();
        properties.setApiBaseUrl("http://api.deepseek.com");
        OpenAiCompatibleAiProviderClient client = new OpenAiCompatibleAiProviderClient(properties, new RestTemplateBuilder());

        AiProviderResult result = client.generateText(prompt());

        assertThat(result.success()).isFalse();
        assertThat(result.errorSummary()).isEqualTo("AI API base URL is missing or invalid.");
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
}
