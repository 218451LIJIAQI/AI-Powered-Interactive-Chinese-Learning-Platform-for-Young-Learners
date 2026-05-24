package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 */
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
}
