package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit coverage for {@code AiProductionGuard}: pins the fail-fast
 * behaviour: production requires the real-provider toggle, a configured
 * API key, a supported provider and an HTTPS base URL; any missing piece
 * aborts startup so production never silently ships unavailable AI.
 */
class AiProductionGuardTests {

    private static final String DEEPSEEK_PROVIDER = "deepseek";
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String API_KEY = "live-ai-key";
    private static final String MODEL_NAME = "deepseek-chat";

    @Test
    void rejectsProdProfileWhenRealProviderIsDisabled() {
        AiProperties properties = configuredProperties();
        properties.setEnableRealProvider(false);

        assertThatThrownBy(() -> new AiProductionGuard(prodEnvironment(), properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_ENABLE_REAL_PROVIDER");
    }

    @Test
    void prodProfileRequiresSupportedProviderWhenRealProviderIsEnabled() {
        AiProperties properties = configuredProperties();
        properties.setProvider("unknown-provider");

        assertThatThrownBy(() -> new AiProductionGuard(prodEnvironment(), properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_PROVIDER");
    }

    @Test
    void prodProfileRequiresApiKeyAndModelWhenRealProviderIsEnabled() {
        AiProperties missingKey = configuredProperties();
        missingKey.setApiKey(" ");
        assertThatThrownBy(() -> new AiProductionGuard(prodEnvironment(), missingKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_API_KEY");

        AiProperties missingModel = configuredProperties();
        missingModel.setModelName(" ");
        assertThatThrownBy(() -> new AiProductionGuard(prodEnvironment(), missingModel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_MODEL_NAME");
    }

    @Test
    void prodProfileRejectsPlaceholderApiKeyWhenRealProviderIsEnabled() {
        AiProperties properties = configuredProperties();
        properties.setApiKey("replace-with-your-deepseek-api-key");

        assertThatThrownBy(() -> new AiProductionGuard(prodEnvironment(), properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void prodProfileRequiresPublicHttpsProviderBaseUrlWhenRealProviderIsEnabled() {
        AiProperties httpBaseUrl = configuredProperties();
        httpBaseUrl.setApiBaseUrl("http://api.deepseek.com");
        assertThatThrownBy(() -> new AiProductionGuard(prodEnvironment(), httpBaseUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        AiProperties loopbackBaseUrl = configuredProperties();
        loopbackBaseUrl.setProvider("openai_compatible");
        loopbackBaseUrl.setApiBaseUrl("https://localhost:11434");
        assertThatThrownBy(() -> new AiProductionGuard(prodEnvironment(), loopbackBaseUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-loopback public host");
    }

    @Test
    void prodProfileAllowsProductionReadyRealProviderConfiguration() {
        assertThatCode(() -> new AiProductionGuard(prodEnvironment(), configuredProperties()))
                .doesNotThrowAnyException();
    }

    private static MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private static AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider(DEEPSEEK_PROVIDER);
        properties.setApiBaseUrl(DEEPSEEK_BASE_URL);
        properties.setApiKey(API_KEY);
        properties.setModelName(MODEL_NAME);
        return properties;
    }
}
