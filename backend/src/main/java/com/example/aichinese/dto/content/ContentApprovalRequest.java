package com.example.aichinese.dto.content;

import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * uses, even if currently only archive consumes it. The platform
 * has since moved to immediate teacher-side publishing (see
 * {@link com.example.aichinese.service.TeacherModuleService}), so
 * the approve/reject codepaths are dormant — the wire format is
 * preserved for forward compatibility with the renewed moderation
 * surface in {@link com.example.aichinese.service.ContentModerationService}.</p>
 *
 * <p>The note is sanitized via {@code AiInputSanitizer.sanitize}
 * before being persisted on
 * {@link com.example.aichinese.entity.ContentModerationRecord}, so
 * any HTML/JS injection from the admin UI is neutralised at the
 * service boundary. The canonical constructor strips surrounding
 * Unicode whitespace and collapses blank notes to {@code null}
 * before {@link Size} validation, matching the request-boundary
 * normalisation used by the other polished request DTOs in this
 * package. The
 * {@value ContentDtoConstants#MODERATION_NOTE_MAX_LENGTH}-char cap
 * mirrors both the {@code content_moderation_records.note} schema
 * column width and the
 * {@code AdminContentReviewService.MAX_MODERATION_NOTE_LENGTH}
 * sanitizer contract — see {@link ContentDtoConstants} for the
 * shared source of truth.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.
 * This matches the convention used across the other polished
 * request DTOs in {@code com.example.aichinese.dto.teacher}.</p>
 *
 * @param note optional admin note attached to this moderation
 *             action; {@code null} or blank produces a
 *             null-stored note; capped at
 *             {@value ContentDtoConstants#MODERATION_NOTE_MAX_LENGTH}
 *             characters to match the underlying schema column
 */
@JsonPropertyOrder({ "note" })
public record ContentApprovalRequest(
        @Size(max = ContentDtoConstants.MODERATION_NOTE_MAX_LENGTH,
                message = "Approval note must not exceed 1000 characters.")
        String note) {

    public ContentApprovalRequest {
        note = stripToNull(note);
    }
}
