package com.example.aichinese.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/admin/system-settings/platform} —
 * the admin updates the platform-wide settings surfaced on
 * {@link AdminPlatformSettingsResponse}.
 *
 * <p>Note that {@link AdminPlatformSettingsResponse#platformName()}
 * is <em>not</em> on this DTO — the platform name is fixed at
 * deployment time via configuration and not editable through
 * the admin UI. The other seven mutable fields are all required
 * (boxed {@link Boolean} with {@link NotNull}) so the wire
 * format always carries an explicit value rather than relying on
 * {@code null}-as-default.</p>
 *
 * <h2>Boxed {@link Boolean} for {@link NotNull}</h2>
 * <p>Each boolean field is the boxed wrapper rather than primitive
 * so {@link NotNull} can distinguish "client forgot to send the
 * field" (rejected) from "client sent {@code false}" (accepted).
 * Without this distinction a missing field would silently
 * default to {@code false} and could disable safety features
 * like {@link #aiSafetyCheck} unintentionally.</p>
 *
 * <p>{@link #aiDisclaimerText} is required and capped at 200
 * chars to fit in the small banner UI. See
 * {@link AdminPlatformSettingsResponse} for the field-level
 * semantics carried through to the response.</p>
 */
public record AdminPlatformSettingsUpdateRequest(
        @NotBlank(message = "AI disclaimer text is required.")
        @Size(max = 200, message = "AI disclaimer text must be 200 characters or fewer.")
        String aiDisclaimerText,
        @NotNull(message = "Email notifications setting is required.")
        Boolean emailNotifications,
        @NotNull(message = "In-app notifications setting is required.")
        Boolean inAppNotifications,
        @NotNull(message = "Daily summary setting is required.")
        Boolean dailySummary,
        @NotNull(message = "Strict content filtering setting is required.")
        Boolean strictContentFiltering,
        @NotNull(message = "Sensitive word filter setting is required.")
        Boolean sensitiveWordFilter,
        @NotNull(message = "AI safety check setting is required.")
        Boolean aiSafetyCheck) {
}
