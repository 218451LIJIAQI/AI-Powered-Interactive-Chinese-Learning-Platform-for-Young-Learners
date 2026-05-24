package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/moderation-actions} — the
 * admin records a free-form moderation note attached to any target
 * row, separate from the structured archive / approve / reject
 * workflows that have their own DTOs
 * ({@link com.example.aichinese.dto.content.ContentApprovalRequest}
 * and friends).
 *
 * <p>The created
 * {@link com.example.aichinese.entity.ContentModerationRecord} is
 * surfaced back to admins via {@link ModerationRecordResponse}.
 * This is the catch-all moderation channel for "I noticed
 * something but it doesn't fit a specific workflow" notes — e.g.
 * "Discussed quality concerns with teacher offline".</p>
 *
 * <h2>{@link #targetType} / {@link #targetId}</h2>
 * <p>{@link #targetType} is a free-form short label (capped at 40
 * chars) identifying the kind of thing being annotated:
 * {@code "Teacher"}, {@code "Lesson"}, {@code "PaymentRecord"},
 * etc. {@link #targetId} is the row id within that table.
 * Together they form a (target_type, target_id) tuple consumed by
 * the admin moderation history view.</p>
 *
 * <p>{@link #reason} is the admin's note (mandatory, capped at
 * 1000 chars to match the schema column). The service sanitises
 * it via {@code AiInputSanitizer} before persistence to neutralise
 * any HTML/JS injection from the admin UI.</p>
 */
public record CreateModerationActionRequest(
        @NotBlank(message = "Target type is required.")
        @Size(max = 40, message = "Target type must be 40 characters or fewer.")
        String targetType,

        @NotNull(message = "Target id is required.")
        @Positive(message = "Target id must be positive.")
        Long targetId,

        @NotBlank(message = "Reason is required.")
        @Size(max = 1000, message = "Reason must be 1000 characters or fewer.")
        String reason) {
}
