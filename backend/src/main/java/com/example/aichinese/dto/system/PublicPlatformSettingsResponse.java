package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "platformName",
        "aiDisclaimerText",
        "sensitiveWordFilter",
        "aiSafetyCheck"
})
public record PublicPlatformSettingsResponse(
        String platformName,
        String aiDisclaimerText,
        boolean sensitiveWordFilter,
        boolean aiSafetyCheck) {
}
