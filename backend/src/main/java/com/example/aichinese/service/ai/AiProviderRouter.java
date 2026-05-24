package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

/**
 * Selects the active {@link AiProviderClient} for every AI feature
 * call.
 *
 * <p>The decision is taken <em>per-call</em> (not once at startup) so
 * a runtime configuration change to
 * {@code app.ai.enable-real-provider} or {@code app.ai.provider} is
 * picked up without a restart. When the real-provider toggle is off,
 * every request gets the {@link NoOpAiProviderClient} which fails
 * cleanly so feature fallbacks kick in. When the toggle is on, the
 * configured provider name — normalised via
 * {@link AiProviderSupport#normalizeProvider(String)} — is matched
 * against the {@link AiProviderSupport#SUPPORTED_PROVIDERS supported
 * set}; an unknown value throws so a typo does not silently degrade to
 * no-op behaviour.</p>
 */
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

    /**
     * Returns the {@link AiProviderClient} that should handle the
     * current request based on the live configuration snapshot.
     *
     * @throws IllegalStateException if the real provider is enabled but
     *         the configured provider name is not in the supported set
     */
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
