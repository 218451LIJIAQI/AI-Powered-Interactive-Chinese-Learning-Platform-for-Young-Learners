package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Detail payload for one teacher-owned module, returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/modules} (paginated list) and
 *       {@code GET /api/v1/teachers/modules/{id}} (detail)</li>
 *   <li>The response side of {@code POST}, {@code PUT},
 *       {@code PATCH .../archive} on the same path</li>
 * </ul>
 *
 * <p>Mirrors {@link CreateModuleRequest} field-for-field plus the
 * server-managed audit metadata:</p>
 * <ul>
 *   <li>{@link #contentStatus} — editorial lifecycle state. Lists
 *       in this endpoint include all statuses (DRAFT, SUBMITTED,
 *       APPROVED, REJECTED, ARCHIVED) so the teacher sees their
 *       full library.</li>
 *   <li>{@link #rejectionReason} — admin's free-text reason from
 *       the latest
 *       {@link com.example.aichinese.entity.enums.ContentStatus#REJECTED}
 *       moderation decision; {@code null} otherwise.</li>
 *   <li>{@link #teacher} — denormalised {@link TeacherOwnerSummaryResponse}
 *       so the admin moderation UI (which reuses this DTO) can
 *       attribute authorship.</li>
 *   <li>{@link #createdAt}/{@link #updatedAt} — managed by the
 *       {@code BaseEntity} lifecycle hooks.</li>
 * </ul>
 *
 * <p>Modules do not carry a {@code learningType}; see the note on
 * {@link CreateModuleRequest}.</p>
 */
public record LearningModuleResponse(
        Long id,
        String title,
        String description,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        AccessType accessType,
        BigDecimal price,
        String coverImageUrl,
        ContentStatus contentStatus,
        String rejectionReason,
        TeacherOwnerSummaryResponse teacher,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
