package com.example.aichinese.service.ai;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

@Component
public class AiProviderRouter {

    private final AiProperties aiProperties;
    private final OpenAiResponsesAiProviderClient responsesClient;
    private final OpenAiCompatibleAiProviderClient openAiCompatibleClient;

    public AiProviderRouter(
            AiProperties aiProperties,
            OpenAiResponsesAiProviderClient responsesClient,
            OpenAiCompatibleAiProviderClient openAiCompatibleClient) {
        this.aiProperties = aiProperties;
        this.responsesClient = responsesClient;
        this.openAiCompatibleClient = openAiCompatibleClient;
    }

    public AiProviderClient resolveClient() {
        String provider = aiProperties.normalizedProvider();

        return switch (provider) {
            case AiProperties.OPENAI -> responsesClient;
            case AiProperties.DEEPSEEK,
                 AiProperties.OPENAI_COMPATIBLE -> openAiCompatibleClient;
            default -> throw new IllegalStateException(
                    "Unsupported AI provider '" + provider
                            + "' while AI_ENABLE_REAL_PROVIDER=true. Supported: "
                            + AiProperties.SUPPORTED_PROVIDERS);
        };
    }
}
