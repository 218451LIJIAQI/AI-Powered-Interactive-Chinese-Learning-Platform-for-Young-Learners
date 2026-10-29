package com.example.aichinese.dto.system;

/**
 * Public (unauthenticated) settings only; the moderation switches stay on the admin endpoint. {@code aiModelName} is
 * the configured AI model (AI_MODEL_NAME) that the responsible-AI page names (FRPUB02), or null when none is set.
 */
public record PublicPlatformSettingsResponse(String aiDisclaimerText, String aiModelName) {
}
