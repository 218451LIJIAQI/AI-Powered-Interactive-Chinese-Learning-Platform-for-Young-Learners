package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Originator of a single message in an AI chat transcript.
 *
 * <p>Mirrors the {@code role} field of the upstream OpenAI-compatible chat
 * completion contract while remaining intentionally narrower: only the two
 * roles that can appear in persisted student transcripts are modelled
 * here. Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code AiChatMessage}, so the constant names are part of the database
 * contract and are also asserted by
 * {@code SchemaEnumContractTests#finalDomainCheckConstraintsContainEveryApplicationEnumValue}
 * — they MUST NOT be renamed or removed without an accompanying Flyway
 * migration and an update to the {@code chk_ai_chat_messages_sender_role}
 * check constraint.</p>
 */
public enum AiChatSenderRole {

    /** Message authored by the human student. */
    USER,

    /** Message authored by the AI assistant (e.g. SparkBot). */
    ASSISTANT;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AiChatSenderRole> LOOKUP =
            EnumLookups.indexByName(AiChatSenderRole.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised sender role
     */
    public static Optional<AiChatSenderRole> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised sender role
     */
    public static AiChatSenderRole fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI chat sender role is required.",
                () -> "Invalid AI chat sender role. Allowed values are USER or ASSISTANT.");
    }

    /** @return {@code true} when this message originated from the human student. */
    public boolean isUser() {
        return this == USER;
    }

    /** @return {@code true} when this message originated from the AI assistant. */
    public boolean isAssistant() {
        return this == ASSISTANT;
    }
}
