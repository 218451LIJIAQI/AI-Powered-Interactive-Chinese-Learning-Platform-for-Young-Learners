package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

/**
 * One row of the public course-discovery catalogue, returned by
 * {@code GET /api/v1/public/courses} (paginated, with filters for
 * keyword / level / category / access type / learning type / sort).
 *
 * <p>The catalogue mixes lessons and packages behind one paginated
 * list (modules are <em>not</em> exposed publicly — only
 * {@link ManagedContentType#LESSON} and
 * {@link ManagedContentType#PACKAGE} surface here, even though
 * {@link ManagedContentType} also has {@code MODULE}). The
 * front-end uses {@link #type} to dispatch the click-through to
 * the appropriate detail endpoint via {@link #id}.</p>
 *
 * <h2>Composite {@link #id}</h2>
 * <p>The id is a {@code String} formatted as {@code "TYPE-N"} —
 * specifically {@code "LESSON-N"} for a row sourced from
 * {@code lessons} or {@code "PACKAGE-N"} for a row sourced from
 * {@code course_packages} (e.g. {@code "LESSON-7"},
 * {@code "PACKAGE-11"}) — so the front-end can use a single id
 * field even when the underlying rows live in different tables.
 * A hyphen (rather than colon) separator avoids any URL-component
 * escaping concerns. The numeric {@link #contentId} is also
 * surfaced for callers that prefer the raw FK. The detail
 * endpoint {@code GET /api/v1/public/courses/{id}} accepts this
 * composite form and parses it via
 * {@code PublicContentService.parseContentType} +
 * {@code parseContentId}. The format is produced by
 * {@link com.example.aichinese.mapper.ContentManagementMapper#toPublicCourseSummary(com.example.aichinese.entity.Lesson)}
 * and the package overload.</p>
 *
 * <h2>Visibility</h2>
 * <p>Only rows with {@link ContentStatus#PUBLISHED} owned by an
 * {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED}
 * teacher reach this list. The {@link #status} field always reads
 * {@code PUBLISHED} on the wire (it is preserved for symmetry with
 * the admin-facing list which can show other statuses).</p>
 *
 * <h2>{@link #locked}</h2>
 * <p>Server-derived: {@code true} when the course is paid and the
 * (possibly unauthenticated) caller does not currently hold access
 * — a free-tier/anonymous visitor sees the card with a paywall
 * badge rather than purchase / preview controls. Primitive
 * {@code boolean} so the JSON wire format always emits a value.</p>
 *
 * <p>{@link #shortDescription} is the front-end teaser line — the
 * service truncates the underlying long description if needed.
 * {@link #rating} is reserved for the future ratings feature; may
 * be {@code null} until ratings ship.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the fields the
 * mapper guarantees from any persisted row ({@link #id},
 * {@link #contentId}, {@link #type}, {@link #title},
 * {@link #accessType}, {@link #status}). Contextual fields
 * ({@link #shortDescription}, {@link #teacher}, {@link #level},
 * {@link #category}, {@link #learningType}, {@link #price},
 * {@link #rating}, {@link #coverImageUrl}) remain nullable per the
 * documented contextual rules.</p>
 *
 * @param id               composite identifier in the form
 *                         {@code "LESSON-N"} or {@code "PACKAGE-N"};
 *                         never {@code null}
 * @param contentId        numeric primary key of the underlying
 *                         lesson or package row; never {@code null}
 * @param type             per-row discriminator
 *                         ({@link ManagedContentType#LESSON} or
 *                         {@link ManagedContentType#PACKAGE});
 *                         never {@code null}
 * @param title            editorial title shown on the catalogue
 *                         card; never {@code null}
 * @param shortDescription teaser line; nullable; truncated
 *                         server-side from the underlying long
 *                         description if needed
 * @param teacher          public-facing teacher reference;
 *                         {@code null} when the teacher profile's
 *                         {@code user} association failed to
 *                         hydrate
 * @param level            taxonomy level reference; nullable
 * @param category         taxonomy category reference; nullable
 * @param learningType     delivery model; nullable on legacy rows
 * @param accessType       commercial access tier ({@code FREE} /
 *                         {@code PAID}); never {@code null}
 * @param price            listed price; nullable for {@code FREE}
 *                         rows that never had a price set
 * @param currencyCode     ISO 4217 code used by checkout for this
 *                         listed price; never {@code null}
 * @param rating           future ratings feature placeholder;
 *                         {@code null} until ratings ship
 * @param coverImageUrl    optional cover image reference;
 *                         nullable
 * @param locked           server-derived access flag; primitive so
 *                         the wire format always emits a value
 * @param status           editorial lifecycle state; always reads
 *                         {@link ContentStatus#PUBLISHED} for
 *                         publicly-listed rows; never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "contentId",
        "type",
        "title",
        "shortDescription",
        "teacher",
        "level",
        "category",
        "learningType",
        "accessType",
        "price",
        "currencyCode",
        "rating",
        "coverImageUrl",
        "locked",
        "status"
})
public record PublicCourseSummaryResponse(
        String id,
        Long contentId,
        ManagedContentType type,
        String title,
        String shortDescription,
        PublicTeacherSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        String currencyCode,
        BigDecimal rating,
        String coverImageUrl,
        boolean locked,
        ContentStatus status) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees from any persisted row so a
     * malformed catalogue row cannot reach the wire format and
     * break the public course-discovery grid (which sorts and
     * filters server-side on these fields and would 500 the entire
     * screen on a {@code null}).
     */
    public PublicCourseSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
