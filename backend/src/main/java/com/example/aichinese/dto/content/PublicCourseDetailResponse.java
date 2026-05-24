package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.util.List;

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
 * {@link PublicCourseSummaryResponse#id()} (e.g.
 * {@code "LESSON:7"}, {@code "PACKAGE:11"}); the service splits it
 * into {@link #type} and {@link #contentId} via
 * {@code PublicContentService.parseContentType} +
 * {@code parseContentId}. Modules ({@link ManagedContentType#MODULE})
 * are not exposed via this endpoint — only lessons and packages.</p>
 *
 * <h2>Same-shape lesson and package</h2>
 * <p>This DTO unifies both lessons and packages behind a single
 * shape so the front-end uses one detail page for both:</p>
 * <ul>
 *   <li>{@link #basicContent} — populated for lessons; null for
 *       packages.</li>
 *   <li>{@link #packageItems} — populated for packages (the
 *       included lessons / modules with denormalised
 *       {@link PublicCourseItemSummaryResponse}); null/empty for
 *       lessons.</li>
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
 *       paywall ("Subscribe to access this lesson"); empty when
 *       not locked.</li>
 * </ul>
 *
 * <p>See {@link PublicCourseSummaryResponse} for the catalogue-feed
 * counterpart and the composite-id format.</p>
 */
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
        String coverImageUrl,
        boolean locked,
        ContentStatus status,
        String basicContent,
        List<PublicCourseItemSummaryResponse> packageItems,
        String callToAction,
        String lockedMessage) {
}
