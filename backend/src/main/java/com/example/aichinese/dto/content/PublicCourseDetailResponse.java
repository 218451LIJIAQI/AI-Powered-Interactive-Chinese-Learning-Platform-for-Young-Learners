package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

/**
 * Detail payload for one publicly-listed course (lesson or
 * package), returned by
 * {@code GET /api/v1/public/courses/{id}}.
 *
 * <p>The path id is the composite string from
 * {@link PublicCourseSummaryResponse#id()}; the service splits it
 * into {@link #type} and {@link #contentId} via
 * {@code PublicContentService.parseContentType} +
 * {@code parseContentId}. See {@link PublicCourseSummaryResponse}
 * for the exact format (a hyphenated {@code TYPE-N} pair).
 * Modules ({@link ManagedContentType#MODULE}) are not exposed via
 * this endpoint — only lessons and packages.</p>
 *
 * <h2>Same-shape lesson and package</h2>
 * <p>This DTO unifies both lessons and packages behind a single
 * shape so the front-end uses one detail page for both:</p>
 * <ul>
 *   <li>{@link #basicContent} — populated for lessons; null for
 *       packages.</li>
 *   <li>{@link #packageItems} — populated for packages (the
 *       included lessons / modules with denormalised
 *       {@link PublicCourseItemSummaryResponse}); an empty
 *       (never {@code null}) immutable list for lessons.</li>
 * </ul>
 *
 * <h2>{@link #locked} / {@link #callToAction} / {@link #lockedMessage}</h2>
 * <p>The "buy this course" front-end uses these three together:</p>
 * <ul>
 *   <li>{@link #locked} — whether the visitor currently has access
 *       (paid / subscribed / free).</li>
 *   <li>{@link #callToAction} — pre-formatted human label for the
 *       primary button. Examples: "Start lesson" (free, accessible),
 *       "Buy now — RM 19.90" (paid, locked), "Continue learning"
 *       (already enrolled). Server-formatted so the front-end does
 *       not duplicate the pricing/access-state branching.</li>
 *   <li>{@link #lockedMessage} — explainer shown beside a locked
 *       paywall ("Subscribe to access this lesson"); {@code null}
 *       when not locked.</li>
 * </ul>
 *
 * <p>See {@link PublicCourseSummaryResponse} for the catalogue-feed
 * counterpart and the composite-id format.</p>
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
 * {@link #accessType}, {@link #status}, {@link #callToAction}).
 * {@link #packageItems} is defensively copied into an immutable
 * list (null-coalesced to {@link List#of()}) so the caller always
 * receives a never-null view.</p>
 *
 * @param id             composite identifier in the form
 *                       {@code "LESSON-N"} or {@code "PACKAGE-N"};
 *                       never {@code null}
 * @param contentId      numeric primary key of the underlying
 *                       lesson or package row; never {@code null}
 * @param type           per-row discriminator
 *                       ({@link ManagedContentType#LESSON} or
 *                       {@link ManagedContentType#PACKAGE});
 *                       never {@code null}
 * @param title          editorial title shown on the detail page;
 *                       never {@code null}
 * @param description    short editorial description (lesson
 *                       {@code summary} or package
 *                       {@code description}); nullable
 * @param teacher        public-facing teacher reference;
 *                       {@code null} when the teacher profile's
 *                       {@code user} association failed to hydrate
 * @param level          taxonomy level reference; nullable
 * @param category       taxonomy category reference; nullable
 * @param learningType   delivery model; nullable on legacy rows
 *                       authored before the column became NOT NULL
 * @param accessType     commercial access tier ({@code FREE} /
 *                       {@code PAID}); never {@code null}
 * @param price          listed price; nullable for {@code FREE}
 *                       rows that never had a price set
 * @param currencyCode   ISO 4217 code used by checkout for this
 *                       listed price; never {@code null}
 * @param coverImageUrl  optional cover image reference; nullable
 * @param locked         server-derived access flag; primitive so
 *                       the wire format always emits a value
 * @param status         editorial lifecycle state; never
 *                       {@code null}
 * @param basicContent   lesson body; {@code null} for packages
 * @param packageItems   bundled items for packages; empty
 *                       (never {@code null}) immutable list for
 *                       lessons
 * @param callToAction   pre-formatted primary-button label; never
 *                       {@code null}
 * @param lockedMessage  paywall explainer; {@code null} when not
 *                       locked
 */
@JsonPropertyOrder({
        "id",
        "contentId",
        "type",
        "title",
        "description",
        "teacher",
        "level",
        "category",
        "learningType",
        "accessType",
        "price",
        "currencyCode",
        "coverImageUrl",
        "locked",
        "status",
        "basicContent",
        "packageItems",
        "callToAction",
        "lockedMessage"
})
public record PublicCourseDetailResponse(
        String id,
        Long contentId,
        ManagedContentType type,
        String title,
        String description,
        PublicTeacherSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        String currencyCode,
        String coverImageUrl,
        boolean locked,
        ContentStatus status,
        String basicContent,
        List<PublicCourseItemSummaryResponse> packageItems,
        String callToAction,
        String lockedMessage) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees from any persisted row, and
     * defensive-copies {@link #packageItems} into an immutable
     * list (null-coalesced to {@link List#of()}). The result is a
     * DTO whose contract — "these fields are always present, this
     * list is never null and never mutates" — holds even if a
     * future caller bypasses the canonical mapper.
     */
    public PublicCourseDetailResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(callToAction, "callToAction must not be null");
        packageItems = packageItems == null ? List.of() : List.copyOf(packageItems);
    }
}
