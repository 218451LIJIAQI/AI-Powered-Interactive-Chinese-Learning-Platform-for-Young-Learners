package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.example.aichinese.service.ai.NoOpAiProviderClient;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.example.aichinese.service.ai.OpenAiResponsesAiProviderClient;

/**
 * Unit coverage for {@code AiProviderRouter}: a disabled real provider routes to the fail-safe
 * {@code NoOpAiProviderClient}; the default "openai" provider routes to the OpenAI Responses API
 * client named in the proposal; alternative Chat Completions providers route to the compatible
 * client; unknown provider names raise rather than silently degrading to no-op.
 */
class AiProviderRouterTests {

    private final OpenAiResponsesAiProviderClient responsesClient = mock(OpenAiResponsesAiProviderClient.class);
    private final OpenAiCompatibleAiProviderClient compatibleClient = mock(OpenAiCompatibleAiProviderClient.class);
    private final NoOpAiProviderClient noOpClient = mock(NoOpAiProviderClient.class);

    @Test
    void disabledRealProviderUsesNoOpClientForLocalAndTestProfiles() {
        assertThat(router(properties(false, null)).resolveClient()).isSameAs(noOpClient);
    }

    @Test
    void defaultProviderUsesOpenAiResponsesClient() {
        assertThat(router(properties(true, null)).resolveClient()).isSameAs(responsesClient);
        assertThat(router(properties(true, "openai")).resolveClient()).isSameAs(responsesClient);
    }

    @Test
    void alternativeProvidersUseOpenAiCompatibleClient() {
        assertThat(router(properties(true, "deepseek")).resolveClient()).isSameAs(compatibleClient);
        assertThat(router(properties(true, "openai_compatible")).resolveClient()).isSameAs(compatibleClient);
    }

    @Test
    void enabledRealProviderRejectsUnknownProviderInsteadOfFallingBackToNoOp() {
        assertThatThrownBy(router(properties(true, "unknown-provider"))::resolveClient)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported AI provider");
    }

    private static AiProperties properties(boolean enableRealProvider, String provider) {
        AiProperties properties = new AiProperties();
        properties.setEnableRealProvider(enableRealProvider);
        properties.setProvider(provider);
        return properties;
    }

    private AiProviderRouter router(AiProperties properties) {
        return new AiProviderRouter(properties, responsesClient, compatibleClient, noOpClient);
    }
}
