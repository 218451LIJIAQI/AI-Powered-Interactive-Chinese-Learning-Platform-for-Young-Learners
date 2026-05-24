package com.example.aichinese.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Token-redemption payload submitted to
 * {@code POST /api/v1/auth/reset-password}.
 *
 * <p>The {@link #token} is the raw reset token from the password-reset
 * email link. {@code AuthService.resetPassword} hashes it (the database
 * stores only the hash — see
 * {@link com.example.aichinese.entity.PasswordResetToken}) and then:</p>
 * <ol>
 *   <li>Rejects the request when the hash does not match any row.</li>
 *   <li>Rejects when {@code usedAt} is non-null (single-use rule).</li>
 *   <li>Rejects when {@code expiresAt} has passed.</li>
 *   <li>Otherwise, updates the password hash, stamps {@code usedAt},
 *       and rotates {@code User.sessionVersion} so any active JWTs are
 *       invalidated atomically.</li>
 * </ol>
 *
 * <p>{@link #password} / {@link #confirmPassword} must match — the
 * service layer's {@code validatePasswords} is the single source of
 * truth for the equality check. The 72-byte cap is BCrypt's intrinsic
 * input limit (longer values would be silently truncated).</p>
 *
 * @param token           raw reset token from the email link
 * @param password        new raw password; hashed with BCrypt before
 *                        storage
 * @param confirmPassword must equal {@link #password}; checked in
 *                        {@code AuthService}
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        @Size(max = 128, message = "Reset token must be at most 128 characters")
        String token,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
        @NotBlank(message = "Confirm password is required")
        @Size(max = 72, message = "Confirm password must be at most 72 characters")
        String confirmPassword) {
}
