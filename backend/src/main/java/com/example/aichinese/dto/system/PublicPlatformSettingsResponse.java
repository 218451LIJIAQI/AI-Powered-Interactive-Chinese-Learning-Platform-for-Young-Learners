package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Public (unauthenticated) settings only; the moderation switches stay on the admin endpoint. */
@JsonPropertyOrder({
        "platformName",
        "aiDisclaimerText"
})
public record PublicPlatformSettingsResponse(
        String platformName,
        String aiDisclaimerText) {
}
