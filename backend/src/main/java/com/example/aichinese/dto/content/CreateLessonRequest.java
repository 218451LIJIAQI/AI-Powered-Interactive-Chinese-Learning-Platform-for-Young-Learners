package com.example.aichinese.dto.content;

import java.math.BigDecimal;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        String description,
        Long moduleId,
        @NotNull(message = "Level is required.")
        Long levelId,
        @NotNull(message = "Category is required.")
        Long categoryId,
        @NotNull(message = "Learning type is required.")
        LearningType learningType,
        @NotNull(message = "Access type is required.")
        AccessType accessType,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0.")
        BigDecimal price,
        @PositiveOrZero(message = "Estimated duration must be greater than or equal to 0.")
        Integer estimatedDurationMinutes,
        String basicContent,
        @Size(max = 255, message = "Cover image reference must not exceed 255 characters.")
        String coverImageUrl) {
}
