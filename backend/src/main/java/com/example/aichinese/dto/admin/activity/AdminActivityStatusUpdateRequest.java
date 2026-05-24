package com.example.aichinese.dto.admin.activity;

import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Body for the per-type status PATCH endpoints under
 * {@code /api/v1/admin/activities/{activityType}/{id}/status}.
 *
 * <p>One uniform payload shape lets the admin "Publish",
 * "Unpublish" and "Archive" buttons share the same backend handler
 * regardless of which of the six activity tables the row lives in.
 * The service layer ({@code AdminActivityService#validateTargetStatus})
 * constrains the <em>allowed</em> target values to:</p>
 *
 * <ul>
 *   <li>{@link ContentStatus#PUBLISHED} — flips the activity into the
 *       student-facing library.</li>
 *   <li>{@link ContentStatus#DRAFT} — pulls a published activity back
 *       into the editing pipeline.</li>
 *   <li>{@link ContentStatus#ARCHIVED} — soft-deletes the activity;
 *       it disappears from new listings but historic
 *       {@code AuditLog} / progress rows continue to reference it.</li>
 * </ul>
 *
 * <p>Other {@link ContentStatus} values (e.g. {@code SUBMITTED},
 * {@code APPROVED}, {@code REJECTED}) are rejected at the service
 * layer with a {@code 400 Bad Request} since the platform-managed
 * activity library does not run an admin-review flow — admins are
 * the authors and approvers in one. Bean Validation deliberately only
 * enforces non-{@code null} here so the service-layer message can
 * remain the single, business-aware source of truth for the
 * "DRAFT/PUBLISHED/ARCHIVED only" rule; embedding the same set
 * twice (once via {@code @AssertTrue}, once via
 * {@code validateTargetStatus}) would risk drift when the
 * lifecycle is extended.</p>
 *
 * <p>{@link #status} is the boxed {@link ContentStatus} (an enum is
 * inherently nullable in JSON deserialisation) deliberately so
 * {@link NotNull} can distinguish a missing/empty body from an
 * explicit value, and so a malformed JSON token is surfaced as a
 * focused 400 rather than a 500.</p>
 *
 * @param status target lifecycle state; required (validation rejects
 *               {@code null})
 */
public record AdminActivityStatusUpdateRequest(
        @NotNull(message = "Target status is required.")
        ContentStatus status) {
}
