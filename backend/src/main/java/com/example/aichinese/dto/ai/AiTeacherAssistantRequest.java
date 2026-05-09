package com.example.aichinese.dto.ai;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTeacherAssistantRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 100, message = "Level must not exceed 100 characters.")
        String level,
        @Size(max = 100, message = "Category must not exceed 100 characters.")
        String category,
        @Size(max = 500, message = "Learning objective must not exceed 500 characters.")
        String learningObjective,
        List<String> activityTypes,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {
}
