package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

/**
 * Default {@link AiProviderClient} active when
 * {@code app.ai.enable-real-provider=false}.
 *
 * <p>Always returns {@link AiProviderResult#failure} so {@link AiService}
 * cleanly delegates to its per-feature deterministic fallback. Picked
 * by {@link AiProviderRouter} whenever the real-provider toggle is
 * off — meaning a fresh deploy without configured AI credentials does
 * not silently leak placeholder responses to learners.</p>
 */
@Component
public class NoOpAiProviderClient implements AiProviderClient {

    private final AiProperties aiProperties;

    public NoOpAiProviderClient(AiProperties aiProperties) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
    }

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        return AiProviderResult.failure(
                AiProviderSupport.normalizeProvider(aiProperties.getProvider()),
                aiProperties.getModelName(),
                "AI provider is currently disabled or unavailable.");
    }
}
