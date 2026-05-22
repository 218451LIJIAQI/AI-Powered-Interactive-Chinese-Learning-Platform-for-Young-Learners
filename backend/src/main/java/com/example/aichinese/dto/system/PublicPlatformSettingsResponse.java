package com.example.aichinese.dto.system;

public record PublicPlatformSettingsResponse(
        String platformName,
        String aiDisclaimerText,
        boolean strictContentFiltering,
        boolean sensitiveWordFilter,
        boolean aiSafetyCheck) {
}
