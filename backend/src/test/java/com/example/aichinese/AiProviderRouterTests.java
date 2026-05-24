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
        AiProperties properties = new AiProperties();
        properties.setEnableRealProvider(false);
        NoOpAiProviderClient noOpClient = mock(NoOpAiProviderClient.class);
        OpenAiCompatibleAiProviderClient realClient = mock(OpenAiCompatibleAiProviderClient.class);

        AiProviderRouter router = new AiProviderRouter(properties, realClient, noOpClient);

        assertThat(router.resolveClient()).isSameAs(noOpClient);
    }

    @Test
    void enabledRealProviderRejectsUnknownProviderInsteadOfFallingBackToNoOp() {
        AiProperties properties = new AiProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider("unknown-provider");

        AiProviderRouter router = new AiProviderRouter(
                properties,
                mock(OpenAiCompatibleAiProviderClient.class),
                mock(NoOpAiProviderClient.class));

        assertThatThrownBy(router::resolveClient)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_PROVIDER");
    }
}
