package com.example.aichinese.dto.system;

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
