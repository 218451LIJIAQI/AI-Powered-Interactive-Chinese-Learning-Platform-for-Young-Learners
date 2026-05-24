package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

/**
 * One row of the admin's cross-type content-moderation list,
 * returned by {@code GET /api/v1/admin/content/submissions}
 * (paginated, with optional filters for {@code contentType},
 * {@code status}, and {@code teacherId}).
 *
 * <p>The list mixes modules, lessons, and packages behind a single
 * paginated stream so the admin can triage the entire publishing
 * pipeline from one screen instead of three. {@link #contentType}
 * (a {@link ManagedContentType}) is the per-row discriminator that
 * tells the front-end which detail endpoint to call when the admin
 * clicks through. The list is server-side sorted by
 * {@link #updatedAt} {@code DESC} so the most recently changed
 * submissions surface first (see
 * {@code AdminContentReviewService.listSubmissions}).</p>
 *
 * <p>Compared to the type-specific responses
 * ({@link LearningModuleResponse}, {@link LessonResponse},
 * {@link CoursePackageResponse}), this DTO drops the lesson-only
 * {@code basicContent} and the package-only {@code packageItems}
 * to keep the cross-type list payload predictable. The full
 * detail (including those fields) is on
 * {@link ContentSubmissionDetailResponse}.</p>
 *
 * <h2>{@link #learningType}</h2>
 * <p>Lessons and packages carry a learning type; modules return
 * {@code null} since modules are content-type-agnostic containers
 * (see the note on {@link CreateModuleRequest}). The list payload
 * carries the type explicitly so the admin filter UI works
 * uniformly.</p>
 *
 * <h2>{@link #contentStatus} / {@link #rejectionReason}</h2>
 * <p>The full historical lifecycle was
 * {@link ContentStatus#DRAFT} → {@code SUBMITTED} → {@code APPROVED}
 * (publishable) or {@code REJECTED} (revision required) →
 * eventually {@code ARCHIVED}. Since the platform moved to
 * immediate teacher-side publishing, newly authored artefacts now
 * jump straight to {@link ContentStatus#PUBLISHED}; the legacy
 * states remain on the wire for rows authored before that
 * migration. {@link #rejectionReason} carries the most recent
 * {@code REJECTED} note when applicable; {@code null} otherwise.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions. The
 * type discriminator stays first so a client can dispatch on it
 * without decoding the rest of the payload.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the fields the
 * mapper guarantees from any persisted row
 * ({@link #contentType}, {@link #id}, {@link #title},
 * {@link #accessType}, {@link #contentStatus}, {@link #createdAt},
 * {@link #updatedAt}). Contextual fields ({@link #description},
 * {@link #teacher}, {@link #level}, {@link #category},
 * {@link #learningType}, {@link #price}, {@link #rejectionReason})
 * remain nullable per the documented contextual rules.</p>
 *
 * @param contentType     discriminator ({@code MODULE}/{@code LESSON}/
 *                        {@code PACKAGE}); never {@code null}
 * @param id              primary key of the underlying row in the
 *                        per-type table; never {@code null}
 * @param title           editorial title shown on the moderation
 *                        list; NOT NULL schema column; never
 *                        {@code null}
 * @param description     short editorial description (lesson
 *                        {@code summary} or module/package
 *                        {@code description}); nullable
 * @param teacher         owning teacher reference; {@code null}
 *                        when the teacher profile's {@code user}
 *                        association failed to hydrate
 * @param level           taxonomy level reference; {@code null}
 *                        when the underlying FK is unset (legacy
 *                        rows)
 * @param category        taxonomy category reference; {@code null}
 *                        when the underlying FK is unset (legacy
 *                        rows)
 * @param learningType    delivery model; {@code null} for module
 *                        rows since modules are content-type-
 *                        agnostic containers
 * @param accessType      commercial access tier ({@code FREE} /
 *                        {@code PAID}); NOT NULL schema column;
 *                        never {@code null}
 * @param price           listed price ({@code DECIMAL(10,2)}); may
 *                        be {@code null} for {@code FREE} rows
 *                        that never had a price set
 * @param contentStatus   editorial lifecycle state; NOT NULL
 *                        schema column; never {@code null}
 * @param rejectionReason latest {@link ContentStatus#REJECTED}
 *                        note when applicable; {@code null}
 *                        otherwise
 * @param createdAt       row creation timestamp from
 *                        {@code BaseEntity}; never {@code null}
 * @param updatedAt       row last-modified timestamp from
 *                        {@code BaseEntity}; never {@code null}
 */
@JsonPropertyOrder({
        "contentType",
        "id",
        "title",
        "description",
        "teacher",
        "level",
        "category",
        "learningType",
        "accessType",
        "price",
        "contentStatus",
        "rejectionReason",
        "createdAt",
        "updatedAt"
})
public record ContentSubmissionResponse(
        ManagedContentType contentType,
        Long id,
        String title,
        String description,
        TeacherOwnerSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        ContentStatus contentStatus,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees from any persisted row so a
     * malformed submission row cannot reach the wire format and
     * break the admin moderation list (the list is sorted on
     * {@link #updatedAt} server-side — a {@code null} would
     * propagate a {@link NullPointerException} through the
     * comparator and 500 the entire admin screen).
     */
    public ContentSubmissionResponse {
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
