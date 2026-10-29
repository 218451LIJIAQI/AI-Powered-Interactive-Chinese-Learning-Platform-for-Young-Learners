package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.example.aichinese.service.ai.OpenAiResponsesAiProviderClient;

/**
 * Unit coverage for {@code AiProviderRouter}: the default "openai" provider routes to the OpenAI Responses API
 * client named in the proposal; alternative Chat Completions providers route to the compatible
 * client; unknown provider names raise.
 */
class AiProviderRouterTests {

    private final OpenAiResponsesAiProviderClient responsesClient = mock(OpenAiResponsesAiProviderClient.class);
    private final OpenAiCompatibleAiProviderClient compatibleClient = mock(OpenAiCompatibleAiProviderClient.class);

    @Test
    void defaultProviderUsesOpenAiResponsesClient() {
        assertThat(router(properties(null)).resolveClient()).isSameAs(responsesClient);
        assertThat(router(properties("openai")).resolveClient()).isSameAs(responsesClient);
    }

    @Test
    void alternativeProvidersUseOpenAiCompatibleClient() {
        assertThat(router(properties("deepseek")).resolveClient()).isSameAs(compatibleClient);
        assertThat(router(properties("openai_compatible")).resolveClient()).isSameAs(compatibleClient);
    }

    @Test
    void unknownProviderIsRejected() {
        assertThatThrownBy(router(properties("unknown-provider"))::resolveClient)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported AI provider");
    }

    private static AiProperties properties(String provider) {
        AiProperties properties = new AiProperties();
        properties.setProvider(provider);
        return properties;
    }

    private AiProviderRouter router(AiProperties properties) {
        return new AiProviderRouter(properties, responsesClient, compatibleClient);
    }
}
