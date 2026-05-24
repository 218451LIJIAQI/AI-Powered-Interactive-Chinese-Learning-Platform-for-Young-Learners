package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Admin-facing platform settings snapshot returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/system-settings/platform}</li>
 *   <li>{@code PUT /api/v1/admin/system-settings/platform}
 *       (response side; echoes the freshly-saved row)</li>
 * </ul>
 *
 * <p>Drives the admin "Platform settings" page. The settings are
 * persisted as key-value rows in
 * {@link com.example.aichinese.entity.SystemSetting} keyed by
 * stable strings (e.g. {@code "platform.name"},
 * {@code "ai.disclaimer.text"}) and rolled up into this DTO for
 * the UI.</p>
 *
 * <h2>Notification preferences</h2>
 * <p>{@link #emailNotifications}, {@link #inAppNotifications},
 * {@link #dailySummary} are platform-wide defaults that govern
 * whether the notification service fans out emails / in-app rows
 * / daily digest emails. Per-user overrides are not yet
 * implemented — these flip the entire fan-out for all users.</p>
 *
 * <h2>Content safety</h2>
 * <p>{@link #strictContentFiltering} engages a stricter sanitiser
 * on user-generated content (teacher feedback, AI prompts).
 * {@link #sensitiveWordFilter} engages the
 * sensitive-word blocklist on student-facing AI inputs.
 * {@link #aiSafetyCheck} runs a secondary safety classification
 * on AI outputs before they reach the student. All three are
 * surfaced separately so admins can tune the safety/UX trade-off
 * independently.</p>
 *
 * <p>{@link #aiDisclaimerText} is the platform-wide AI banner
 * shown above every AI surface (e.g. "AI responses are best-
 * effort and may contain errors — verify with your teacher when
 * unsure."). Editable by admins; mirrored on
 * {@link PublicPlatformSettingsResponse} so unauthenticated
 * visitors also see the disclaimer.</p>
 *
 * <p>The lighter {@link PublicPlatformSettingsResponse} is the
 * subset visible without authentication.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions
 * (snapshot tests, the admin settings page form layout). The
 * primitive {@code boolean} flags always emit
 * {@code true}/{@code false} so the front-end never has to
 * tri-state on {@code null}.</p>
 */
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
