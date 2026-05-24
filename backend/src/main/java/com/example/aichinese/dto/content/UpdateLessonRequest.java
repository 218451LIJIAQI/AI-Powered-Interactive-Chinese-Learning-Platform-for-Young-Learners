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
 * Body for {@code PUT /api/v1/teachers/lessons/{id}} — the teacher
 * edits an existing lesson.
 *
 * <p>Mirrors {@link CreateLessonRequest} field-for-field. The
 * service rejects edits when the lesson is in a non-editable state
 * (see {@code TeacherContentAccessService.validateEditableStatus})
 * and requires the edit to come from the lesson's owning teacher.</p>
 *
 * <p>{@link #moduleId} can be changed to move the lesson to a
 * different module (or set to {@code null} to detach the lesson).
 * Quizzes and recorded videos travel with the lesson.</p>
 *
 * <p>For archive use the dedicated
 * {@code PATCH /api/v1/teachers/lessons/{id}/archive} endpoint
 * (no body).</p>
 *
 * <p>See {@link CreateLessonRequest} and {@link CreateModuleRequest}
 * for the full discussion of fields and shared validation rules.</p>
 */
public record UpdateLessonRequest(
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
