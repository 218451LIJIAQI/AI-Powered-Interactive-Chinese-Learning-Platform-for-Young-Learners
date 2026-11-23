package com.example.aichinese.dto.system;

/**
 * Public (unauthenticated) settings only; the moderation switches stay on the admin endpoint. {@code aiModelName} is
 * the configured AI model (AI_MODEL_NAME) that the responsible-AI page names (FRPUB02), or null when none is set.
 * {@code aiProvider} is the provider AI requests go to (AI_PROVIDER in lower case: openai, deepseek or
 * openai-compatible), or null while no request can reach a provider (the AI Helper is off or not fully configured),
 * so the privacy and responsible-AI pages say what really leaves the server.
 */
public record PublicPlatformSettingsResponse(String aiDisclaimerText, String aiModelName, String aiProvider) {
}
