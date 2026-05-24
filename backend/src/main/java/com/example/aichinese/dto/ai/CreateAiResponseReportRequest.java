package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/students/ai-reports} — a student
 * (or parent on a young child's behalf) flags an AI response as
 * problematic. Persisted as
 * {@link com.example.aichinese.entity.AiResponseReport} for admin
 * triage.
 *
 * <p>Critical safety surface for the platform's bounded-AI
 * stance: the AI is treated as a "best-effort tutor", not an
 * authoritative source — when it produces something incorrect,
 * inappropriate, or harmful, the student must have a low-friction
 * report channel. This request creates a moderation ticket
 * surfaced to admins via {@link AdminAiResponseReportResponse}.</p>
 *
 * <h2>Three optional reference ids</h2>
 * <p>{@link #aiUsageLogId}, {@link #chatSessionId},
 * {@link #chatMessageId} are all {@link Positive} and
 * <em>optional</em> — at least one should be set so admins can
 * pivot to the underlying record. Use cases:</p>
 * <ul>
 *   <li>Chat: client sends {@link #chatMessageId} (and optionally
 *       {@link #chatSessionId}) so the admin can review the full
 *       conversation context.</li>
 *   <li>Single-shot AI call (vocabulary helper, quiz gen, etc.):
 *       client sends {@link #aiUsageLogId} so the admin sees the
 *       prompt + response pair on
 *       {@link AdminAiUsageLogResponse}.</li>
 * </ul>
 *
 * <h2>Snapshots</h2>
 * <p>{@link #promptSnapshot} and {@link #aiResponseSnapshot} are
 * client-supplied excerpts (capped at 4000 chars) to lock in
 * <em>what the student actually saw</em>. Keeping the snapshot on
 * the report row guards against later edits/deletes of the
 * underlying chat-message row losing the evidence; it also lets
 * the admin reviewer act on the report without rebuilding the
 * full conversation context.</p>
 *
 * <p>{@link #reasonCategory} is a short tag
 * ({@code "INACCURATE"}, {@code "INAPPROPRIATE"},
 * {@code "OFF_TOPIC"}, {@code "OTHER"}). {@link #description} is
 * the optional free-text elaboration.</p>
 */
public record CreateAiResponseReportRequest(
        @Positive(message = "AI usage log id must be positive.")
        Long aiUsageLogId,
        @Positive(message = "Chat session id must be positive.")
        Long chatSessionId,
        @Positive(message = "Chat message id must be positive.")
        Long chatMessageId,

        @NotBlank(message = "AI feature key is required.")
        @Size(max = 100, message = "AI feature key must be 100 characters or fewer.")
        String featureKey,

        @NotBlank(message = "Report reason category is required.")
        @Size(max = 40, message = "Report reason category must be 40 characters or fewer.")
        String reasonCategory,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
        String description,

        @Size(max = 4000, message = "Prompt snapshot must be 4000 characters or fewer.")
        String promptSnapshot,

        @Size(max = 4000, message = "AI response snapshot must be 4000 characters or fewer.")
        String aiResponseSnapshot) {
}
