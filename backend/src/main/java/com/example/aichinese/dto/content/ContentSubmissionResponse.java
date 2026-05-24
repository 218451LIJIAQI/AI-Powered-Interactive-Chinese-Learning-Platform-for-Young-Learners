package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

/**
 * One row of the admin's cross-type content-moderation list,
 * returned by {@code GET /api/v1/admin/content/submissions}
 * (paginated, with optional filters for {@code contentType},
 * {@code status}, and {@code teacherId}).
 *
 * <p>The list mixes modules, lessons, and packages behind a single
 * paginated stream so the admin can triage the entire publishing
 * pipeline from one screen instead of three. {@link #contentType}
 * (a {@link ManagedContentType}) is the per-row discriminator that
 * tells the front-end which detail endpoint to call when the admin
 * clicks through. The list is server-side sorted by
 * {@link #updatedAt} {@code DESC} so the most recently changed
 * submissions surface first.</p>
 *
 * <p>Compared to the type-specific responses
 * ({@link LearningModuleResponse}, {@link LessonResponse},
 * {@link CoursePackageResponse}), this DTO drops the lesson-only
 * {@code basicContent} and the package-only {@code packageItems}
 * to keep the cross-type list payload predictable. The full
 * detail (including those fields) is on
 * {@link ContentSubmissionDetailResponse}.</p>
 *
 * <h2>{@link #learningType}</h2>
 * <p>Lessons and packages carry a learning type; modules return
 * {@code null} since modules are content-type-agnostic containers
 * (see the note on {@link CreateModuleRequest}). The list payload
 * carries the type explicitly so the admin filter UI works
 * uniformly.</p>
 *
 * <h2>{@link #contentStatus} / {@link #rejectionReason}</h2>
 * <p>The full lifecycle is
 * {@link ContentStatus#DRAFT} → {@code SUBMITTED} → {@code APPROVED}
 * (publishable) or {@code REJECTED} (revision required) →
 * eventually {@code ARCHIVED}. {@link #rejectionReason} carries
 * the most recent {@code REJECTED} note when applicable;
 * {@code null} otherwise.</p>
 */
public record ContentSubmissionResponse(
        ManagedContentType contentType,
        Long id,
        String title,
        String description,
        TeacherOwnerSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        ContentStatus contentStatus,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
