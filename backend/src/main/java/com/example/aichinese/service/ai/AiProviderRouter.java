package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

@Component
public class AiProviderRouter {

    private final AiProperties aiProperties;
    private final OpenAiCompatibleAiProviderClient openAiClient;
    private final NoOpAiProviderClient noOpClient;

    public AiProviderRouter(
            AiProperties aiProperties,
            OpenAiCompatibleAiProviderClient openAiClient,
            NoOpAiProviderClient noOpClient) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
        this.openAiClient = Objects.requireNonNull(openAiClient, "openAiClient");
        this.noOpClient = Objects.requireNonNull(noOpClient, "noOpClient");
    }

    public AiProviderClient resolveClient() {
        if (!aiProperties.isEnableRealProvider()) {
            return noOpClient;
        }

        String provider = AiProviderSupport.normalizeProvider(aiProperties.getProvider());

        return switch (provider) {
            case AiProviderSupport.DEFAULT_PROVIDER,
                 AiProviderSupport.OPENAI_COMPATIBLE -> openAiClient;
            default -> throw new IllegalStateException(
                    "Unsupported AI provider '" + provider
                            + "' while AI_ENABLE_REAL_PROVIDER=true. Supported: "
                            + AiProviderSupport.SUPPORTED_PROVIDERS);
        };
    }
}
