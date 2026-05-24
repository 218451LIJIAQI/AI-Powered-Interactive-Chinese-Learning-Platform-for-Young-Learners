package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;

/**
 * Aggregate-root payload for one course package, returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/packages} (paginated list) and
 *       {@code GET /api/v1/teachers/packages/{id}} (detail)</li>
 *   <li>The response side of {@code POST}, {@code PUT},
 *       {@code POST .../items}, {@code DELETE .../items/{itemId}},
 *       {@code PATCH .../archive} on the same path</li>
 * </ul>
 *
 * <p>Mirrors {@link CreateCoursePackageRequest} field-for-field
 * plus the server-managed audit fields and the embedded
 * {@link #packageItems} list. Returning the freshly-saved aggregate
 * from every mutating endpoint lets the front-end skip a follow-up
 * GET when the teacher adds or removes an item.</p>
 *
 * <h2>{@link #packageItems}</h2>
 * <p>Emitted in {@code ORDER BY sort_order ASC, created_at ASC} —
 * the canonical authoring order, which the student-facing catalogue
 * preserves. Each entry is a {@link PackageItemResponse} carrying
 * the underlying lesson/module's {@code titleSnapshot} so a renamed
 * lesson does not retroactively rename it inside the package
 * (the snapshot is captured at insert time). The list is
 * defensively copied through {@link List#copyOf} (and
 * {@code null}-coalesced to {@link List#of()}) so callers always
 * receive a never-null, immutable view.</p>
 *
 * <p>See {@link LearningModuleResponse} for the audit-metadata
 * convention shared with the other owned-content responses.</p>
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
 * {@link #contentStatus}, {@link #createdAt}, {@link #updatedAt}) so
 * a malformed package row cannot reach the wire format and break
 * the teacher dashboard. {@link #packageItems} is never
 * {@code null} on the wire.</p>
 *
 * @param id              package primary key; never {@code null}
 * @param title           editorial title shown on cards;
 *                        NOT NULL schema column; never {@code null}
 * @param description     long-form description (max 2000 chars);
 *                        nullable
 * @param level           taxonomy level reference; {@code null}
 *                        when the underlying FK is unset
 * @param category        taxonomy category reference; {@code null}
 *                        when the underlying FK is unset
 * @param learningType    headline delivery model surfaced on the
 *                        catalogue card; NOT NULL schema column
 *                        (defaults to {@code HYBRID} at the entity
 *                        layer); never {@code null}
 * @param accessType      commercial access tier ({@code FREE} /
 *                        {@code PAID}); NOT NULL schema column;
 *                        never {@code null}
 * @param price           listed price ({@code DECIMAL(10,2)}); may
 *                        be {@code null} for {@code FREE} packages
 * @param coverImageUrl   optional cover image reference (max
 *                        255 chars); nullable
 * @param contentStatus   editorial lifecycle state; NOT NULL
 *                        schema column; never {@code null}
 * @param rejectionReason latest moderator rejection note when
 *                        applicable; {@code null} otherwise
 * @param teacher         owning teacher reference; {@code null}
 *                        when the teacher profile's {@code user}
 *                        association failed to hydrate
 * @param packageItems    bundled lessons / modules in canonical
 *                        sort order; immutable; never {@code null}
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
        "learningType",
        "accessType",
        "price",
        "coverImageUrl",
        "contentStatus",
        "rejectionReason",
        "teacher",
        "packageItems",
        "createdAt",
        "updatedAt"
})
public record CoursePackageResponse(
        Long id,
        String title,
        String description,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        String coverImageUrl,
        ContentStatus contentStatus,
        String rejectionReason,
        TeacherOwnerSummaryResponse teacher,
        List<PackageItemResponse> packageItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees from any persisted row, and
     * defensive-copies {@link #packageItems} into an immutable list
     * (null-coalesced to {@link List#of()}). The list contract
     * holds even if a future caller bypasses the canonical mapper.
     */
    public CoursePackageResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(learningType, "learningType must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        packageItems = packageItems == null ? List.of() : List.copyOf(packageItems);
    }
}
