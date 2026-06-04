package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "platformName",
        "aiDisclaimerText",
        "strictContentFiltering",
        "sensitiveWordFilter",
        "aiSafetyCheck"
})
public record PublicPlatformSettingsResponse(
        String platformName,
        String aiDisclaimerText,
        boolean strictContentFiltering,
        boolean sensitiveWordFilter,
        boolean aiSafetyCheck) {
}
