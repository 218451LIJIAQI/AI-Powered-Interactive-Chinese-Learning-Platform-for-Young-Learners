package com.example.aichinese.dto.content;

import jakarta.validation.constraints.Size;

/**
 * Moderation-note carrier used by the admin content-review
 * endpoints when the admin's action benefits from an attached
 * free-text reason.
 *
 * <p>Currently consumed by:</p>
 * <ul>
 *   <li>{@code PATCH /api/v1/admin/content/{contentType}/{id}/archive}
 *       — the archive action with an optional admin note. The
 *       {@code @RequestBody(required = false)} on the controller
 *       method means clients may omit the body entirely; in that
 *       case the service still archives, just without a recorded
 *       note.</li>
 * </ul>
 *
 * <p>The DTO is named "approval request" because it was originally
 * scoped for the broader approve / reject / archive moderation
 * flow; the single-{@link #note} shape covers all three intended
 * uses, even if currently only archive consumes it.</p>
 *
 * <p>The note is sanitized via {@code AiInputSanitizer.sanitize}
 * before being persisted on
 * {@link com.example.aichinese.entity.ContentModerationRecord}, so
 * any HTML/JS injection from the admin UI is neutralised. The
 * 1000-char cap matches the underlying schema column.</p>
 *
 * @param note optional admin note attached to this moderation
 *             action; {@code null} or blank produces a
 *             null-stored note
 */
public record ContentApprovalRequest(
        @Size(max = 1000, message = "Approval note must not exceed 1000 characters.")
        String note) {
}
