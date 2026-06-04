package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum AiChatSenderRole {

    USER,

    ASSISTANT;

    private static final Map<String, AiChatSenderRole> LOOKUP =
            EnumLookups.indexByName(AiChatSenderRole.class);

    public static Optional<AiChatSenderRole> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static AiChatSenderRole fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI chat sender role is required.",
                () -> "Invalid AI chat sender role. Allowed values are USER or ASSISTANT.");
    }

    public boolean isUser() {
        return this == USER;
    }

    public boolean isAssistant() {
        return this == ASSISTANT;
    }
}
