package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Platform settings that actually take effect: the read-only platform name, the AI disclaimer
 * shown on AI features, the sensitive-word filter for teacher content and the AI safety check
 * for learner AI requests and replies.
 */
@JsonPropertyOrder({
        "platformName",
        "aiDisclaimerText",
        "sensitiveWordFilter",
        "aiSafetyCheck"
})
public record AdminPlatformSettingsResponse(
        String platformName,
        String aiDisclaimerText,
        boolean sensitiveWordFilter,
        boolean aiSafetyCheck) {
}
