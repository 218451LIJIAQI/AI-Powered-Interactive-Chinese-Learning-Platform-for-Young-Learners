package com.example.aichinese.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Forgot-password payload submitted to
 * {@code POST /api/v1/auth/forgot-password}.
 *
 * <p><b>Account-enumeration resistance.</b> The endpoint always returns
 * the same generic success message — "If the email is registered, a
 * password reset instruction will be sent..." — regardless of whether
 * the supplied email actually corresponds to a {@code User} row. An
 * attacker therefore cannot use this endpoint to probe which emails
 * are registered. The internal
 * {@code AuthService.forgotPassword} branches silently:</p>
 * <ul>
 *   <li><b>Email matches an active user:</b> a single-use
 *       {@code PasswordResetToken} is created and emailed.</li>
 *   <li><b>Email is unknown or inactive:</b> no token is created,
 *       no email is sent, and the same generic 200 response is
 *       returned.</li>
 * </ul>
 *
 * <p><b>Rate limiting.</b> Independent of account existence,
 * {@code PublicAuthRequestLimiter.checkForgotPasswordAllowed} caps the
 * per-IP-and-per-email request rate so the endpoint cannot be abused
 * to flood mailboxes or measure response timing differences.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * surrounding whitespace before Bean Validation runs, so a stray space
 * in a copy-pasted email address does not slip past
 * {@link Size @Size(max=320)} (which would otherwise count the
 * whitespace toward the length cap) and reaches
 * {@code AuthService.normalizeEmail} in canonical form. {@code null}
 * is preserved so {@link NotBlank @NotBlank} surfaces the friendly
 * "Email is required" message rather than a low-level
 * {@code NullPointerException}. Lower-casing is intentionally left to
 * the service layer to keep the DTO purely a wire-format contract.</p>
 *
 * @param email the address to send the reset instruction to; trimmed
 *              by the canonical constructor and case-normalised
 *              server-side
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email) {

    /**
     * Defensive canonical constructor: trims surrounding whitespace so
     * Bean Validation operates on the canonical form of the email and
     * the downstream service receives a value identical to what
     * {@code AuthService.normalizeEmail} would produce on the trim
     * step. {@code null} is preserved for {@link NotBlank @NotBlank}
     * to render the user-facing "Email is required" message instead
     * of a {@link NullPointerException}.
     */
    public ForgotPasswordRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
