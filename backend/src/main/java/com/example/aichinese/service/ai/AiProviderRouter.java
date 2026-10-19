package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

@Component
public class AiProviderRouter {

    private final AiProperties aiProperties;
    private final OpenAiResponsesAiProviderClient responsesClient;
    private final OpenAiCompatibleAiProviderClient openAiCompatibleClient;
    private final NoOpAiProviderClient noOpClient;

    public AiProviderRouter(
            AiProperties aiProperties,
            OpenAiResponsesAiProviderClient responsesClient,
            OpenAiCompatibleAiProviderClient openAiCompatibleClient,
            NoOpAiProviderClient noOpClient) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
        this.responsesClient = Objects.requireNonNull(responsesClient, "responsesClient");
        this.openAiCompatibleClient = Objects.requireNonNull(openAiCompatibleClient, "openAiCompatibleClient");
        this.noOpClient = Objects.requireNonNull(noOpClient, "noOpClient");
    }

    public AiProviderClient resolveClient() {
        if (!aiProperties.isEnableRealProvider()) {
            return noOpClient;
        }

        String provider = AiProviderSupport.normalizeProvider(aiProperties.getProvider());

        return switch (provider) {
            case AiProviderSupport.OPENAI -> responsesClient;
            case AiProviderSupport.DEEPSEEK,
                 AiProviderSupport.OPENAI_COMPATIBLE -> openAiCompatibleClient;
            default -> throw new IllegalStateException(
                    "Unsupported AI provider '" + provider
                            + "' while AI_ENABLE_REAL_PROVIDER=true. Supported: "
                            + AiProviderSupport.SUPPORTED_PROVIDERS);
        };
    }
}
