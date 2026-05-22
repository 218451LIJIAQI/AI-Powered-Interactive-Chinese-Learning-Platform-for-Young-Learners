package com.example.aichinese.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminPlatformSettingsUpdateRequest(
        @NotBlank(message = "AI disclaimer text is required.")
        @Size(max = 200, message = "AI disclaimer text must be 200 characters or fewer.")
        String aiDisclaimerText,
        @NotNull(message = "Email notifications setting is required.")
        Boolean emailNotifications,
        @NotNull(message = "In-app notifications setting is required.")
        Boolean inAppNotifications,
        @NotNull(message = "Daily summary setting is required.")
        Boolean dailySummary,
        @NotNull(message = "Strict content filtering setting is required.")
        Boolean strictContentFiltering,
        @NotNull(message = "Sensitive word filter setting is required.")
        Boolean sensitiveWordFilter,
        @NotNull(message = "AI safety check setting is required.")
        Boolean aiSafetyCheck) {
}
