package com.example.aichinese.dto.content;

import com.example.aichinese.entity.enums.LessonActivityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLessonActivityRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,
        @NotNull(message = "Activity type is required.")
        LessonActivityType activityType,
        @Size(max = 1000, message = "Instruction must not exceed 1000 characters.")
        String instruction,
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        String description,
        Integer displayOrder,
        String placeholderContent) {
}
