package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;

/**
 * Detail payload for one teacher-owned lesson, returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/lessons} (paginated list) and
 *       {@code GET /api/v1/teachers/lessons/{id}} (detail)</li>
 *   <li>The response side of {@code POST}, {@code PUT},
 *       {@code PATCH .../archive} on the same path</li>
 * </ul>
 *
 * <p>Mirrors {@link CreateLessonRequest} field-for-field plus the
 * server-computed audit fields. Compared to
 * {@link LearningModuleResponse}, this DTO additionally carries:</p>
 * <ul>
 *   <li>{@link #moduleTitle} — denormalised parent module title to
 *       avoid a follow-up GET when the teacher's lesson list is
 *       grouped under module headings.</li>
 *   <li>{@link #learningType} — drives the player renderer; see
 *       {@link CreateLessonRequest}.</li>
 *   <li>{@link #estimatedDurationMinutes} — UX hint.</li>
 *   <li>{@link #basicContent} — full authored body.</li>
 * </ul>
 *
 * <p>The student-facing counterpart is
 * {@code StudentLessonPlayerResponse}, which wraps this body with
 * progress-aware fields ({@code completionPercent},
 * {@code completed}, {@code locked}) and the embedded live-class
 * and recorded-video lists.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the fields the
 * mapper guarantees from any persisted row ({@link #id},
 * {@link #title}, {@link #learningType}, {@link #accessType},
 * {@link #contentStatus}, {@link #createdAt}, {@link #updatedAt}).
 * Contextual fields ({@link #description}, {@link #basicContent},
 * {@link #moduleId}, {@link #moduleTitle}, {@link #level},
 * {@link #category}, {@link #price},
 * {@link #estimatedDurationMinutes}, {@link #coverImageUrl},
 * {@link #rejectionReason}, {@link #teacher}) remain nullable per
 * the documented contextual rules.</p>
 *
 * @param id                       lesson primary key; never
 *                                 {@code null}
 * @param title                    editorial title shown on cards;
 *                                 NOT NULL schema column; never
 *                                 {@code null}
 * @param description              short editorial description
 *                                 (lesson {@code summary} column);
 *                                 nullable
 * @param basicContent             authored rich-text body
 *                                 ({@code LONGTEXT}); nullable
 * @param moduleId                 parent module FK when the lesson
 *                                 belongs to one; {@code null} for
 *                                 stand-alone lessons
 * @param moduleTitle              denormalised parent module title;
 *                                 {@code null} when {@link #moduleId}
 *                                 is {@code null}
 * @param level                    taxonomy level reference;
 *                                 {@code null} when the underlying
 *                                 FK is unset
 * @param category                 taxonomy category reference;
 *                                 {@code null} when the underlying
 *                                 FK is unset
 * @param learningType             delivery model; NOT NULL schema
 *                                 column (defaults to
 *                                 {@code RECORDED} at the entity
 *                                 layer); never {@code null}
 * @param accessType               commercial access tier
 *                                 ({@code FREE} / {@code PAID});
 *                                 NOT NULL schema column; never
 *                                 {@code null}
 * @param price                    listed price ({@code DECIMAL(10,2)});
 *                                 may be {@code null} for
 *                                 {@code FREE} lessons
 * @param estimatedDurationMinutes UX duration hint (minutes);
 *                                 nullable
 * @param coverImageUrl            optional cover image reference;
 *                                 nullable
 * @param contentStatus            editorial lifecycle state; NOT
 *                                 NULL schema column; never
 *                                 {@code null}
 * @param rejectionReason          latest moderator rejection note
 *                                 when applicable; {@code null}
 *                                 otherwise
 * @param teacher                  owning teacher reference;
 *                                 {@code null} when the teacher
 *                                 profile's {@code user}
 *                                 association failed to hydrate
 * @param createdAt                row creation timestamp from
 *                                 {@code BaseEntity}; never
 *                                 {@code null}
 * @param updatedAt                row last-modified timestamp from
 *                                 {@code BaseEntity}; never
 *                                 {@code null}
 */
@JsonPropertyOrder({
        "id",
        "title",
        "description",
        "basicContent",
        "moduleId",
        "moduleTitle",
        "level",
        "category",
        "learningType",
        "accessType",
        "price",
        "estimatedDurationMinutes",
        "coverImageUrl",
        "contentStatus",
        "rejectionReason",
        "teacher",
        "createdAt",
        "updatedAt"
})
public record LessonResponse(
        Long id,
        String title,
        String description,
        String basicContent,
        Long moduleId,
        String moduleTitle,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        Integer estimatedDurationMinutes,
        String coverImageUrl,
        ContentStatus contentStatus,
        String rejectionReason,
        TeacherOwnerSummaryResponse teacher,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees from any persisted lesson row
     * so a malformed row cannot reach the wire format and break
     * either the teacher dashboard or the student player.
     */
    public LessonResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(learningType, "learningType must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
