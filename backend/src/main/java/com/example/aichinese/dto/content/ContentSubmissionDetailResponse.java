package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
 *       {@link CoursePackageResponse}.</li>
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
 */
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
}
