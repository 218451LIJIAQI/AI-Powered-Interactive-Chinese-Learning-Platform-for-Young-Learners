package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Detail payload for one teacher-owned module, returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/modules} (paginated list) and
 *       {@code GET /api/v1/teachers/modules/{id}} (detail)</li>
 *   <li>The response side of {@code POST}, {@code PUT},
 *       {@code PATCH .../archive} on the same path</li>
 * </ul>
 *
 * <p>Mirrors {@link CreateModuleRequest} field-for-field plus the
 * server-managed audit metadata:</p>
 * <ul>
 *   <li>{@link #contentStatus} — editorial lifecycle state. Lists
 *       in this endpoint include all statuses
 *       ({@code DRAFT}, {@code SUBMITTED}, {@code APPROVED},
 *       {@code REJECTED}, {@code PUBLISHED}, {@code ARCHIVED}) so
 *       the teacher sees their full library. Newly authored
 *       modules jump straight to
 *       {@link ContentStatus#PUBLISHED} (see
 *       {@link CreateModuleRequest}); the legacy intermediate
 *       states are only ever seen on rows authored before the
 *       immediate-publish migration.</li>
 *   <li>{@link #rejectionReason} — admin's free-text reason from
 *       the latest
 *       {@link ContentStatus#REJECTED} moderation decision;
 *       {@code null} otherwise.</li>
 *   <li>{@link #teacher} — denormalised
 *       {@link TeacherOwnerSummaryResponse} so the admin moderation
 *       UI (which reuses this DTO) can attribute authorship.</li>
 *   <li>{@link #createdAt}/{@link #updatedAt} — managed by the
 *       {@code BaseEntity} lifecycle hooks.</li>
 * </ul>
 *
 * <p>Modules do not carry a {@code learningType}; see the note on
 * {@link CreateModuleRequest}.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the fields the
 * mapper guarantees from any persisted row ({@link #id},
 * {@link #title}, {@link #accessType}, {@link #contentStatus},
 * {@link #createdAt}, {@link #updatedAt}). Contextual fields
 * ({@link #description}, {@link #level}, {@link #category},
 * {@link #price}, {@link #coverImageUrl}, {@link #rejectionReason},
 * {@link #teacher}) remain nullable per the documented contextual
 * rules.</p>
 *
 * @param id              module primary key; never {@code null}
 * @param title           editorial title shown on cards; NOT NULL
 *                        schema column; never {@code null}
 * @param description     long-form description (max 2000 chars);
 *                        nullable
 * @param level           taxonomy level reference; {@code null}
 *                        when the underlying FK is unset
 * @param category        taxonomy category reference; {@code null}
 *                        when the underlying FK is unset
 * @param accessType      commercial access tier ({@code FREE} /
 *                        {@code PAID}); NOT NULL schema column;
 *                        never {@code null}
 * @param price           listed price ({@code DECIMAL(10,2)}); may
 *                        be {@code null} for {@code FREE} modules
 * @param coverImageUrl   optional cover image reference; nullable
 * @param contentStatus   editorial lifecycle state; NOT NULL schema
 *                        column; never {@code null}
 * @param rejectionReason latest moderator rejection note when
 *                        applicable; {@code null} otherwise
 * @param teacher         owning teacher reference; {@code null}
 *                        when the teacher profile's {@code user}
 *                        association failed to hydrate
 * @param createdAt       row creation timestamp from
 *                        {@code BaseEntity}; never {@code null}
 * @param updatedAt       row last-modified timestamp from
 *                        {@code BaseEntity}; never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "title",
        "description",
        "level",
        "category",
        "accessType",
        "price",
        "coverImageUrl",
        "contentStatus",
        "rejectionReason",
        "teacher",
        "createdAt",
        "updatedAt"
})
public record LearningModuleResponse(
        Long id,
        String title,
        String description,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        AccessType accessType,
        BigDecimal price,
        String coverImageUrl,
        ContentStatus contentStatus,
        String rejectionReason,
        TeacherOwnerSummaryResponse teacher,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees from any persisted row so a
     * malformed module row cannot reach the wire format and break
     * the teacher / admin dashboard.
     */
    public LearningModuleResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
