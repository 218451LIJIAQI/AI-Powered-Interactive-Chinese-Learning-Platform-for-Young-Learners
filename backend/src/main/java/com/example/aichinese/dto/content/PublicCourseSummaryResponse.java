package com.example.aichinese.dto.content;

import java.math.BigDecimal;

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
 * <p>The id is a {@code String} formatted as {@code "TYPE:N"} (e.g.
 * {@code "LESSON:7"}, {@code "PACKAGE:11"}) so the front-end can
 * use a single id field even when the underlying rows live in
 * different tables. The numeric {@link #contentId} is also surfaced
 * for callers that prefer the raw FK. The detail endpoint
 * {@code GET /api/v1/public/courses/{id}} accepts this composite
 * form and parses it via {@code PublicContentService.parseContentType}
 * + {@code parseContentId}.</p>
 *
 * <h2>Visibility</h2>
 * <p>Only rows with
 * {@link ContentStatus#PUBLISHED} owned by an
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
 */
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
        BigDecimal rating,
        String coverImageUrl,
        boolean locked,
        ContentStatus status) {
}
