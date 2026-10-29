package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAiSettingsUpdateRequest(
        @NotBlank(message = "AI disclaimer text is required.")
        @Size(max = 200, message = "AI disclaimer text must be 200 characters or fewer.")
        String aiNoticeText,
        @NotNull(message = "Sensitive word filter setting is required.")
        Boolean sensitiveWordFilter,
        @NotNull(message = "AI safety check setting is required.")
        Boolean aiSafetyCheck) {

    public AdminAiSettingsUpdateRequest {
        aiNoticeText = strip(aiNoticeText);
    }
}
