package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit coverage for {@code AiProductionGuard} — pins the fail-fast
 * behaviour: production requires the real-provider toggle, a configured
 * API key, a supported provider and an HTTPS base URL; any missing piece
 * aborts startup so production never silently ships unavailable AI.
 */
class AiProductionGuardTests {

    @Test
    void rejectsProdProfileWhenRealProviderIsDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(false);

        assertThatThrownBy(() -> new AiProductionGuard(environment, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_ENABLE_REAL_PROVIDER");
    }

    @Test
    void prodProfileRequiresSupportedProviderWhenRealProviderIsEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AiProperties properties = configuredProperties();
        properties.setProvider("unknown-provider");

        assertThatThrownBy(() -> new AiProductionGuard(environment, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_PROVIDER");
    }

    @Test
    void prodProfileRequiresApiKeyAndModelWhenRealProviderIsEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiProperties missingKey = configuredProperties();
        missingKey.setApiKey(" ");
        assertThatThrownBy(() -> new AiProductionGuard(environment, missingKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_API_KEY");

        AiProperties missingModel = configuredProperties();
        missingModel.setModelName(" ");
        assertThatThrownBy(() -> new AiProductionGuard(environment, missingModel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_MODEL_NAME");
    }

    @Test
    void prodProfileRejectsPlaceholderApiKeyWhenRealProviderIsEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AiProperties properties = configuredProperties();
        properties.setApiKey("replace-with-your-deepseek-api-key");

        assertThatThrownBy(() -> new AiProductionGuard(environment, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void prodProfileRequiresPublicHttpsProviderBaseUrlWhenRealProviderIsEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiProperties httpBaseUrl = configuredProperties();
        httpBaseUrl.setApiBaseUrl("http://api.deepseek.com");
        assertThatThrownBy(() -> new AiProductionGuard(environment, httpBaseUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        AiProperties loopbackBaseUrl = configuredProperties();
        loopbackBaseUrl.setProvider("openai_compatible");
        loopbackBaseUrl.setApiBaseUrl("https://localhost:11434");
        assertThatThrownBy(() -> new AiProductionGuard(environment, loopbackBaseUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-loopback public host");
    }

    @Test
    void prodProfileAllowsProductionReadyRealProviderConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new AiProductionGuard(environment, configuredProperties()))
                .doesNotThrowAnyException();
    }

    private AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider("deepseek");
        properties.setApiBaseUrl("https://api.deepseek.com");
        properties.setApiKey("live-ai-key");
        properties.setModelName("deepseek-chat");
        return properties;
    }
}
