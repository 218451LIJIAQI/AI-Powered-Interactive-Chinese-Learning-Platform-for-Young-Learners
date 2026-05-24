package com.example.aichinese.dto.content;

import java.math.BigDecimal;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/lessons} — an approved
 * teacher creates a new {@link com.example.aichinese.entity.Lesson}.
 *
 * <p>A lesson is the smallest learner-facing learning artefact.
 * Lessons may stand alone (sold individually), be grouped under a
 * {@link com.example.aichinese.entity.LearningModule} via
 * {@link #moduleId}, or be bundled into a
 * {@link com.example.aichinese.entity.CoursePackage} via
 * {@link PackageItemRequest}. Quizzes and recorded videos are
 * attached to lessons in follow-up calls
 * ({@link com.example.aichinese.dto.learning.CreateQuizRequest},
 * {@link com.example.aichinese.dto.learning.RecordedLessonVideoRequest}).</p>
 *
 * <h2>Lesson-specific fields beyond the shared metadata vocabulary</h2>
 * <ul>
 *   <li>{@link #moduleId} — optional FK to a parent module. When
 *       set, the lesson appears in that module's lesson list. The
 *       service validates the module is owned by the same teacher.</li>
 *   <li>{@link #learningType} —
 *       {@link com.example.aichinese.entity.enums.LearningType}
 *       (VIDEO / INTERACTIVE / TEXT / etc.) drives which player
 *       renderer the student-side
 *       {@code StudentLessonPlayerResponse} flips on.</li>
 *   <li>{@link #estimatedDurationMinutes} — UX hint surfaced as
 *       "~15 min" on lesson cards. Required to be non-negative;
 *       {@code 0} or {@code null} both mean "not specified" and
 *       suppress the hint.</li>
 *   <li>{@link #basicContent} — the core authored content
 *       (markdown / structured text), capped at 10 000 chars to
 *       match the {@code TEXT} schema column. Rich learning content
 *       (videos, quizzes, live classes) is attached separately via
 *       the dedicated endpoints listed above.</li>
 * </ul>
 *
 * <p>See {@link CreateModuleRequest} for the shared metadata
 * vocabulary contract that this DTO conforms to (level, category,
 * accessType, price, coverImageUrl).</p>
 */
public record CreateLessonRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        String description,
        @Positive(message = "Module id must be positive.")
        Long moduleId,
        @NotNull(message = "Level is required.")
        @Positive(message = "Level id must be positive.")
        Long levelId,
        @NotNull(message = "Category is required.")
        @Positive(message = "Category id must be positive.")
        Long categoryId,
        @NotNull(message = "Learning type is required.")
        LearningType learningType,
        @NotNull(message = "Access type is required.")
        AccessType accessType,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0.")
        @Digits(integer = 8, fraction = 2, message = "Price must fit within 8 integer digits and 2 decimal places.")
        BigDecimal price,
        @PositiveOrZero(message = "Estimated duration must be greater than or equal to 0.")
        Integer estimatedDurationMinutes,
        @Size(max = 10000, message = "Lesson content must not exceed 10000 characters.")
        String basicContent,
        @Size(max = 255, message = "Cover image reference must not exceed 255 characters.")
        String coverImageUrl) {
}
