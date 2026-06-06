package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.example.aichinese.service.ai.NoOpAiProviderClient;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;

/**
 * Unit coverage for {@code AiProviderRouter} — proves the
 * per-call selection logic: disabled real-provider routes to the
 * fail-safe {@code NoOpAiProviderClient}, supported provider names
 * route to {@code OpenAiCompatibleAiProviderClient}, and unknown
 * provider names raise rather than silently degrading to no-op.
 */
class AiProviderRouterTests {

    @Test
    void disabledRealProviderUsesNoOpClientForLocalAndTestProfiles() {
        NoOpAiProviderClient noOpClient = mock(NoOpAiProviderClient.class);
        AiProviderRouter router = router(properties(false, null), mock(OpenAiCompatibleAiProviderClient.class), noOpClient);

        assertThat(router.resolveClient()).isSameAs(noOpClient);
    }

    @Test
    void enabledRealProviderUsesOpenAiCompatibleClientForSupportedProvider() {
        OpenAiCompatibleAiProviderClient realClient = mock(OpenAiCompatibleAiProviderClient.class);
        AiProviderRouter router = router(properties(true, "deepseek"), realClient, mock(NoOpAiProviderClient.class));

        assertThat(router.resolveClient()).isSameAs(realClient);
    }

    @Test
    void enabledRealProviderRejectsUnknownProviderInsteadOfFallingBackToNoOp() {
        AiProviderRouter router = router(
                properties(true, "unknown-provider"),
                mock(OpenAiCompatibleAiProviderClient.class),
                mock(NoOpAiProviderClient.class));

        assertThatThrownBy(router::resolveClient)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported AI provider");
    }

    private static AiProperties properties(boolean enableRealProvider, String provider) {
        AiProperties properties = new AiProperties();
        properties.setEnableRealProvider(enableRealProvider);
        properties.setProvider(provider);
        return properties;
    }

    private static AiProviderRouter router(
            AiProperties properties,
            OpenAiCompatibleAiProviderClient realClient,
            NoOpAiProviderClient noOpClient) {
        return new AiProviderRouter(properties, realClient, noOpClient);
    }
}
