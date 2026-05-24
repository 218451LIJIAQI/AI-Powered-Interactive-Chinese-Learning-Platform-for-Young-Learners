package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Lean public-facing platform settings snapshot returned by
 * {@code GET /api/v1/public/platform-settings} — accessible to
 * unauthenticated visitors.
 *
 * <p>Drives the front-end's platform-name in the page title /
 * header, the AI disclaimer banner shown above every AI surface,
 * and the safety badges on the public catalogue. Strictly leaner
 * than {@link AdminPlatformSettingsResponse}:</p>
 * <ul>
 *   <li>Drops the notification preferences
 *       ({@code emailNotifications}, {@code inAppNotifications},
 *       {@code dailySummary}) — meaningless to unauthenticated
 *       visitors who do not yet have an account to receive
 *       notifications on.</li>
 *   <li>Keeps the safety flags
 *       ({@link #strictContentFiltering},
 *       {@link #sensitiveWordFilter}, {@link #aiSafetyCheck})
 *       so the front-end can render "Safe AI" / "Family-friendly"
 *       trust signals on the marketing pages.</li>
 *   <li>Keeps {@link #aiDisclaimerText} so the AI disclaimer can
 *       render before the user signs in (e.g. on the
 *       homepage AI demo).</li>
 * </ul>
 *
 * <p>Cached client-side; the front-end refreshes on app load and
 * on settings-change events from the admin (the latter is not yet
 * pushed in real time — admins typically tell users to refresh).</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions
 * (front-end branding/disclaimer rendering, marketing-page trust
 * badges, snapshot tests). The primitive {@code boolean} flags
 * always emit {@code true}/{@code false} so the front-end never
 * has to tri-state on {@code null}.</p>
 */
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
