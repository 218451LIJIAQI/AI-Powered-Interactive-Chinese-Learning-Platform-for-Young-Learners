package com.example.aichinese.dto.system;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * {@link #aiDisclaimerText} so Bean Validation operates on the
 * canonical form (a whitespace-only banner correctly fails
 * {@link NotBlank @NotBlank} after trimming) and the service
 * layer's existing {@code .trim()} pass becomes idempotent.
 * {@code null} is preserved so the {@link NotBlank @NotBlank}
 * validator renders its friendly user-facing message rather than
 * a low-level {@link NullPointerException}. The seven boxed
 * {@link Boolean} flags are intentionally <em>not</em> normalised
 * here because the {@link NotNull @NotNull} check requires the
 * original {@code null}-vs-{@code false} distinction that the
 * boxed wrapper preserves.</p>
 */
@JsonPropertyOrder({
        "aiDisclaimerText",
        "emailNotifications",
        "inAppNotifications",
        "dailySummary",
        "strictContentFiltering",
        "sensitiveWordFilter",
        "aiSafetyCheck"
})
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

    /**
     * Defensive canonical constructor: trims
     * {@link #aiDisclaimerText} so Bean Validation operates on the
     * canonical form and the service layer's existing
     * {@code .trim()} pass is idempotent. {@code null} is
     * preserved so {@link NotBlank @NotBlank} can render its
     * friendly user-facing message rather than a low-level
     * {@link NullPointerException}.
     */
    public AdminPlatformSettingsUpdateRequest {
        if (aiDisclaimerText != null) {
            aiDisclaimerText = aiDisclaimerText.trim();
        }
    }
}
