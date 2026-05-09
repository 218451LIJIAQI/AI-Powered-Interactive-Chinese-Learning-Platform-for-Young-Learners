package com.example.aichinese.dto.learning;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UpdateListeningExerciseRequest(
        @NotBlank(message = "Listening exercise title is required.")
        @Size(max = 200, message = "Listening exercise title must not exceed 200 characters.")
        String title,
        @NotBlank(message = "Chinese text is required.")
        String chineseText,
        String pinyin,
        @Size(max = 255, message = "Audio reference must not exceed 255 characters.")
        String audioUrl,
        @Valid
        @NotEmpty(message = "At least one listening option is required.")
        List<ListeningOptionRequest> options,
        String explanation) {
}
