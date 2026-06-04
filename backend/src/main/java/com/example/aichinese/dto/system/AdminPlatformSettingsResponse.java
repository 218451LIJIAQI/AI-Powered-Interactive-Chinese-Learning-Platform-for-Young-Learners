package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "platformName",
        "aiDisclaimerText",
        "emailNotifications",
        "inAppNotifications",
        "dailySummary",
        "strictContentFiltering",
        "sensitiveWordFilter",
        "aiSafetyCheck"
})
public record AdminPlatformSettingsResponse(
        String platformName,
        String aiDisclaimerText,
        boolean emailNotifications,
        boolean inAppNotifications,
        boolean dailySummary,
        boolean strictContentFiltering,
        boolean sensitiveWordFilter,
        boolean aiSafetyCheck) {
}
