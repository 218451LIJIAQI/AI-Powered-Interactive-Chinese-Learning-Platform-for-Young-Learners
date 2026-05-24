package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Result of one AI chat turn returned by
 * {@code POST /api/v1/ai/chat}.
 *
 * <p>Echoes back both sides of the turn ({@link #userMessage} and
 * {@link #aiResponse}) so the front-end can append the round to
 * the conversation history without trusting its local copy. The
 * canonical message rows are persisted as
 * {@link com.example.aichinese.entity.AiChatMessage} on the
 * server.</p>
 *
 * <h2>{@link #sessionId}</h2>
 * <p>Always populated — when the request omitted a session id,
 * the server creates a fresh session and returns the new id here.
 * The front-end uses it on the next
 * {@link AiChatRequest#sessionId} to continue the conversation.</p>
 *
 * <h2>{@link #status}</h2>
 * <p>Short token mirroring
 * {@link com.example.aichinese.entity.enums.AiUsageStatus} so the
 * client can branch on success/failure without parsing the
 * response body. Common values: {@code "SUCCESS"},
 * {@code "RATE_LIMITED"}, {@code "PROVIDER_ERROR"},
 * {@code "FILTERED"} (when the safety check rejected the
 * response).</p>
 *
 * <p>{@link #language} is the language the AI replied in (echoes
 * the request's preference); {@link #createdAt} is the
 * server-side persist time of the AI message row.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions
 * (snapshot tests, log fingerprints, frontend that maps
 * conversation history). Content is intentionally <em>not</em>
 * redacted in {@code toString()} because both {@link #userMessage}
 * and {@link #aiResponse} are returned to the same student that
 * produced them — the data is by definition known to the caller.</p>
 */
@JsonPropertyOrder({ "sessionId", "userMessage", "aiResponse", "language", "createdAt", "status" })
public record AiChatResponse(
        Long sessionId,
        String userMessage,
        String aiResponse,
        String language,
        LocalDateTime createdAt,
        String status) {
}
