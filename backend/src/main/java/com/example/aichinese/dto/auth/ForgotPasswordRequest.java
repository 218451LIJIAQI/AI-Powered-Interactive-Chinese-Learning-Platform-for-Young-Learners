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
 * @param email the address to send the reset instruction to; case
 *              normalisation happens server-side
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email) {
}
