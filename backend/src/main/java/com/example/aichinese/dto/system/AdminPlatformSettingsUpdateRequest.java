package com.example.aichinese.dto.system;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "aiDisclaimerText",
        "sensitiveWordFilter",
        "aiSafetyCheck"
})
public record AdminPlatformSettingsUpdateRequest(
        @NotBlank(message = "AI disclaimer text is required.")
        @Size(max = 200, message = "AI disclaimer text must be 200 characters or fewer.")
        String aiDisclaimerText,
        @NotNull(message = "Sensitive word filter setting is required.")
        Boolean sensitiveWordFilter,
        @NotNull(message = "AI safety check setting is required.")
        Boolean aiSafetyCheck) {

    public AdminPlatformSettingsUpdateRequest {
        aiDisclaimerText = strip(aiDisclaimerText);
    }
}
