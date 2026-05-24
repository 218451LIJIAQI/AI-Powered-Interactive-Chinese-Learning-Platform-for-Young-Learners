package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Admin-facing moderation history row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/moderation-records} (paginated
 *       cross-target list, newest first)</li>
 *   <li>{@code GET /api/v1/admin/moderation-records/{id}}
 *       (single-row lookup)</li>
 *   <li>The five after-publish moderation endpoints
 *       ({@code POST /api/v1/admin/moderation/hide},
 *       {@code /unpublish}, {@code /archive},
 *       {@code /request-revision}, {@code /restore}) which echo the
 *       freshly-created row from
 *       {@link CreateModerationActionRequest}</li>
 * </ul>
 *
 * <p>Drives the admin's "Recent moderation actions" screen,
 * surfacing every row from
 * {@link com.example.aichinese.entity.ContentModerationRecord} —
 * which is written by both
 * {@link com.example.aichinese.service.ContentModerationService}
 * (the after-publish HIDE / UNPUBLISH / ARCHIVE / REQUEST_REVISION
 * / RESTORE flows driven by {@link CreateModerationActionRequest})
 * and
 * {@link com.example.aichinese.service.AdminContentReviewService}
 * (the lifecycle ARCHIVE flow that runs through the structured
 * content-approval pipeline).</p>
 *
 * <h2>{@link #targetType} / {@link #targetId} / {@link #targetTitle}</h2>
 * <p>{@code (targetType, targetId)} mirror the discriminator pair
 * persisted on the entity ({@code NOT NULL}). {@link #targetTitle}
 * is the {@code target_title_snapshot} column — the title of the
 * resource at the moment the moderation row was written, captured
 * by name so the admin sees "HSK 1 Foundation Pack" rather than
 * just "PACKAGE:11" in the action log even if the resource is later
 * renamed or deleted. {@link #targetTitle} may be {@code null} when
 * the producer did not snapshot a title (older rows or
 * target-types without a natural display title).</p>
 *
 * <h2>{@link #actionTaken}</h2>
 * <p>Short label identifying the action so the admin UI can render
 * category-appropriate icons. The values written by the platform
 * today are the {@link com.example.aichinese.entity.enums.ContentModerationAction}
 * names ({@code HIDE}, {@code UNPUBLISH}, {@code ARCHIVE},
 * {@code REQUEST_REVISION}, {@code RESTORE}) plus the literal
 * {@code "ARCHIVE"} written by the structured approval pipeline.
 * The DTO carries the raw string rather than the enum so historical
 * rows authored by older code paths still deserialise.</p>
 *
 * <h2>{@link #adminUserId} / {@link #adminName}</h2>
 * <p>Identify the admin who recorded the action. Both may be
 * {@code null} for legacy rows where the {@code admin_user_id}
 * association is absent or where the related {@code User} no longer
 * exposes a display name; the schema does not enforce a foreign-key
 * NOT NULL.</p>
 *
 * <h2>{@link #relatedTeacherId} / {@link #relatedTeacherName}</h2>
 * <p>Populated when the moderation target is teacher-owned (a
 * lesson / module / package owned by a teacher) so the admin can
 * pivot directly to the affected teacher's profile. {@code null}
 * for non-teacher targets and for any teacher-owned row whose
 * teacher account has since been deleted.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #targetType}, {@link #targetId},
 *       {@link #actionTaken} and {@link #createdAt} are always
 *       non-{@code null} — they map to the primary key, the
 *       {@code NOT NULL} discriminator columns, and the managed
 *       timestamp on
 *       {@link com.example.aichinese.entity.base.BaseEntity}.</li>
 *   <li>{@link #targetTitle}, {@link #note}, {@link #adminUserId},
 *       {@link #adminName}, {@link #relatedTeacherId} and
 *       {@link #relatedTeacherName} may be {@code null}.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id                  primary key of the underlying
 *                            {@link com.example.aichinese.entity.ContentModerationRecord};
 *                            never {@code null}
 * @param targetType          discriminator identifying the kind of
 *                            resource that was moderated (e.g.
 *                            {@code "LESSON"}, {@code "PACKAGE"});
 *                            never {@code null}
 * @param targetId            primary key of the affected resource
 *                            within the table implied by
 *                            {@link #targetType}; never {@code null}
 * @param targetTitle         best-effort display title captured at
 *                            moderation time; may be {@code null}
 * @param actionTaken         action name written by the producer
 *                            (typically a
 *                            {@link com.example.aichinese.entity.enums.ContentModerationAction}
 *                            name); never {@code null}
 * @param note                free-text admin justification, already
 *                            sanitised by the producer; may be
 *                            {@code null}
 * @param adminUserId         primary key of the recording admin's
 *                            {@link com.example.aichinese.entity.User};
 *                            may be {@code null} for orphaned rows
 * @param adminName           display name of the recording admin;
 *                            may be {@code null}
 * @param relatedTeacherId    primary key of the affected
 *                            {@link com.example.aichinese.entity.TeacherProfile}
 *                            when the target is teacher-owned; may
 *                            be {@code null}
 * @param relatedTeacherName  display name of the affected teacher;
 *                            may be {@code null}
 * @param createdAt           timestamp the moderation row was
 *                            written; never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "targetType",
        "targetId",
        "targetTitle",
        "actionTaken",
        "note",
        "adminUserId",
        "adminName",
        "relatedTeacherId",
        "relatedTeacherName",
        "createdAt"
})
public record ModerationRecordResponse(
        Long id,
        String targetType,
        Long targetId,
        String targetTitle,
        String actionTaken,
        String note,
        Long adminUserId,
        String adminName,
        Long relatedTeacherId,
        String relatedTeacherName,
        LocalDateTime createdAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the persistence layer guarantees ({@link #id},
     * {@link #targetType}, {@link #targetId}, {@link #actionTaken},
     * {@link #createdAt}). All remaining fields are intentionally
     * permitted to be {@code null} per the invariants documented
     * above.
     */
    public ModerationRecordResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(actionTaken, "actionTaken must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
