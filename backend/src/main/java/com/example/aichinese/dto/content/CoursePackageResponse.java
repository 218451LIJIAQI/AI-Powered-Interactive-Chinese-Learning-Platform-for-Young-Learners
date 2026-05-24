package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
 * (the snapshot is captured at insert time).</p>
 *
 * <p>See {@link LearningModuleResponse} for the audit-metadata
 * convention shared with the other owned-content responses.</p>
 */
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
}
