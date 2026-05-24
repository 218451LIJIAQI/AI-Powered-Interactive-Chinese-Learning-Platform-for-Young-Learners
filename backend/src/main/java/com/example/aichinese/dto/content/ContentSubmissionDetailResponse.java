package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

/**
 * Cross-type detail payload returned by
 * {@code GET /api/v1/admin/content/submissions/{contentType}/{id}}
 * and the response side of
 * {@code PATCH /api/v1/admin/content/{contentType}/{id}/archive}.
 *
 * <p>Unifies module / lesson / package detail behind a single
 * shape so the admin moderation UI can render any of the three
 * artefact types from one detail panel. Compared to
 * {@link ContentSubmissionResponse} (the cross-type list row),
 * this DTO additionally surfaces:</p>
 * <ul>
 *   <li>{@link #moduleId}/{@link #moduleTitle} — populated only
 *       for {@code LESSON} rows that belong to a module; null
 *       for stand-alone lessons, modules, and packages.</li>
 *   <li>{@link #basicContent} — populated only for
 *       {@code LESSON} rows.</li>
 *   <li>{@link #coverImageUrl} — populated for any artefact that
 *       has one set.</li>
 *   <li>{@link #packageItems} — populated only for
 *       {@code PACKAGE} rows; uses the same
 *       {@link PackageItemResponse} as the teacher-facing
 *       {@link CoursePackageResponse}. Non-package rows emit an
 *       empty list (never {@code null}) so the front-end can
 *       iterate unconditionally.</li>
 * </ul>
 *
 * <p>Fields not relevant to the row's {@link #contentType} are
 * {@code null} on the wire (e.g. {@code basicContent} on a module
 * row). The admin UI treats the absence of a field as "not
 * applicable" rather than "not yet authored".</p>
 *
 * <p>See {@link ContentSubmissionResponse} for the moderation
 * lifecycle ({@link ContentStatus} state machine) and
 * {@link ManagedContentType} for the per-row type discriminator.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions (snapshot
 * tests, log fingerprints, etc.). The chosen order keeps the type
 * discriminator first so a client can dispatch on it without
 * decoding the rest of the payload.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the fields the
 * mapper guarantees from any persisted row
 * ({@link #contentType}, {@link #id}, {@link #title},
 * {@link #accessType}, {@link #contentStatus}, {@link #createdAt},
 * {@link #updatedAt}) so a malformed detail row cannot reach the
 * wire format and break the admin moderation panel. The
 * {@link #packageItems} list is defensively copied through
 * {@link List#copyOf} (and {@code null}-coalesced to
 * {@link List#of()}) to give callers an immutable, never-null view.</p>
 *
 * @param contentType    discriminator ({@code MODULE}/{@code LESSON}/
 *                       {@code PACKAGE}); never {@code null}
 * @param id             primary key of the underlying row in the
 *                       per-type table; never {@code null}
 * @param title          editorial title shown on moderation cards;
 *                       NOT NULL schema column; never {@code null}
 * @param description    optional long-form description (lesson
 *                       {@code summary} or module/package
 *                       {@code description}); nullable
 * @param basicContent   lesson-only authored body ({@code LONGTEXT});
 *                       {@code null} for modules and packages
 * @param moduleId       parent module FK for lessons that belong to
 *                       one; {@code null} for stand-alone lessons,
 *                       modules, and packages
 * @param moduleTitle    denormalised parent module title;
 *                       {@code null} when {@link #moduleId} is
 *                       {@code null}
 * @param teacher        owning teacher reference; {@code null} when
 *                       the teacher profile's {@code user}
 *                       association failed to hydrate
 * @param level          taxonomy level reference; {@code null} when
 *                       the underlying FK is unset (legacy rows)
 * @param category       taxonomy category reference; {@code null}
 *                       when the underlying FK is unset (legacy
 *                       rows)
 * @param learningType   delivery model ({@code RECORDED}/{@code LIVE}/
 *                       {@code HYBRID}/etc.); {@code null} for
 *                       module rows (modules are content-type-
 *                       agnostic; see {@link CreateModuleRequest})
 * @param accessType     commercial access tier ({@code FREE} /
 *                       {@code PAID}); NOT NULL schema column;
 *                       never {@code null}
 * @param price          listed price; matches {@code DECIMAL(10,2)};
 *                       may be {@code null} for {@code FREE}
 *                       artefacts that never had a price set
 * @param coverImageUrl  optional cover image reference; nullable
 * @param contentStatus  editorial lifecycle state; NOT NULL schema
 *                       column; never {@code null}
 * @param rejectionReason latest moderator rejection note when
 *                       applicable; {@code null} otherwise
 * @param packageItems   bundled items for {@code PACKAGE} rows;
 *                       empty (never {@code null}) for non-package
 *                       rows; immutable
 * @param createdAt      row creation timestamp from {@code BaseEntity};
 *                       never {@code null}
 * @param updatedAt      row last-modified timestamp from
 *                       {@code BaseEntity}; never {@code null}
 */
@JsonPropertyOrder({
        "contentType",
        "id",
        "title",
        "description",
        "basicContent",
        "moduleId",
        "moduleTitle",
        "teacher",
        "level",
        "category",
        "learningType",
        "accessType",
        "price",
        "coverImageUrl",
        "contentStatus",
        "rejectionReason",
        "packageItems",
        "createdAt",
        "updatedAt"
})
public record ContentSubmissionDetailResponse(
        ManagedContentType contentType,
        Long id,
        String title,
        String description,
        String basicContent,
        Long moduleId,
        String moduleTitle,
        TeacherOwnerSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        String coverImageUrl,
        ContentStatus contentStatus,
        String rejectionReason,
        List<PackageItemResponse> packageItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees from any persisted row, and
     * defensive-copies {@link #packageItems} into an immutable list
     * (null-coalesced to {@link List#of()}). The result is a DTO
     * whose contract — "these fields are always present, this list
     * is never null and never mutates" — holds even if a future
     * caller bypasses the canonical mapper.
     */
    public ContentSubmissionDetailResponse {
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        packageItems = packageItems == null ? List.of() : List.copyOf(packageItems);
    }
}
