package com.example.aichinese.dto.content;

import java.math.BigDecimal;

import com.example.aichinese.entity.enums.AccessType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/modules} — an approved
 * teacher creates a new {@link com.example.aichinese.entity.LearningModule}
 * (a grouping of lessons, e.g. "Greetings & Introductions").
 *
 * <p>The newly created module starts in
 * {@link com.example.aichinese.entity.enums.ContentStatus#DRAFT};
 * the teacher must submit it for admin review before it can reach
 * the student-facing catalogue. Only
 * {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED}
 * teachers can call this endpoint
 * (gated by {@code TeacherContentAccessService.requireApprovedTeacher}).</p>
 *
 * <h2>Shared content metadata vocabulary</h2>
 * <p>Every owned-content create/update request in this package
 * ({@link CreateModuleRequest}, {@link CreateLessonRequest},
 * {@link CreateCoursePackageRequest} and their update counterparts)
 * shares the same metadata fields so the front-end can bind one
 * authoring form to all three artefact types:</p>
 * <ul>
 *   <li>{@link #levelId} — required FK into
 *       {@link LearningLevelResponse} (HSK/level taxonomy).</li>
 *   <li>{@link #categoryId} — required FK into
 *       {@link LearningCategoryResponse} (topic taxonomy).</li>
 *   <li>{@link #accessType} —
 *       {@link com.example.aichinese.entity.enums.AccessType}
 *       (FREE / PAID / SUBSCRIPTION).</li>
 *   <li>{@link #price} — uses {@code @Digits(integer = 8, fraction = 2)}
 *       to match the {@code DECIMAL(10,2)} schema column. Required to
 *       be non-negative; {@code 0} for free content.</li>
 *   <li>{@link #coverImageUrl} — optional reference (URL or relative
 *       path) into the asset store; capped at 255 chars.</li>
 * </ul>
 *
 * <p>Note: modules do <em>not</em> carry a
 * {@link com.example.aichinese.entity.enums.LearningType}; that
 * field is on lessons only. A module is a content-type-agnostic
 * container — its constituent lessons each pick their own learning
 * type.</p>
 */
public record CreateModuleRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = 2000, message = "Description must not exceed 2000 characters.")
        String description,
        @NotNull(message = "Level is required.")
        @Positive(message = "Level id must be positive.")
        Long levelId,
        @NotNull(message = "Category is required.")
        @Positive(message = "Category id must be positive.")
        Long categoryId,
        @NotNull(message = "Access type is required.")
        AccessType accessType,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0.")
        @Digits(integer = 8, fraction = 2, message = "Price must fit within 8 integer digits and 2 decimal places.")
        BigDecimal price,
        @Size(max = 255, message = "Cover image reference must not exceed 255 characters.")
        String coverImageUrl) {
}
