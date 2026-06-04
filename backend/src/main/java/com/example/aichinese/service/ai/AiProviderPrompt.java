package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Objects;

public record AiProviderPrompt(
        String featureKey,
        String systemInstruction,
        String userPrompt,
        String languagePreference,
        int maxOutputTokens,
        boolean expectJson,
        List<AiMessage> messageHistory) {

    public AiProviderPrompt {
        requireNonBlank(featureKey, "featureKey");
        requireNonBlank(systemInstruction, "systemInstruction");
        requireNonBlank(userPrompt, "userPrompt");
        requireNonBlank(languagePreference, "languagePreference");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be > 0, got " + maxOutputTokens);
        }
        Objects.requireNonNull(messageHistory, "messageHistory");
        messageHistory = List.copyOf(messageHistory);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
    }

    public record AiMessage(String role, String content) {

        public AiMessage {
            Objects.requireNonNull(role, "role");
            if (role.isBlank()) {
                throw new IllegalArgumentException("role must not be blank.");
            }
            Objects.requireNonNull(content, "content");
        }
    }
}
