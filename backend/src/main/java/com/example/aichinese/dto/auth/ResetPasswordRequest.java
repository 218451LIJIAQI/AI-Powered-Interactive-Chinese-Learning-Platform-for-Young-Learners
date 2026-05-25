package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.AssertTrue;
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
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#strip() strips} the
 * {@link #token} so a copy-pasted token with stray whitespace from a
 * mail client still hashes to the same value as the canonical token,
 * and so {@link Size @Size(max=128)} is evaluated against the canonical
 * form. Password fields are intentionally <em>not</em> stripped:
 * leading or trailing spaces are significant characters bound directly
 * to the BCrypt hash.</p>
 *
 * <h2>Security</h2>
 * <p>This DTO carries <em>three</em> distinct secrets that must never
 * appear in logs:</p>
 * <ul>
 *   <li>{@link #token} — within its TTL window, the raw reset token is
 *       a single-use credential equivalent to a one-time password;
 *       leaking it would let an attacker hijack the account before
 *       the legitimate user redeems the link.</li>
 *   <li>{@link #password} — the new plaintext password the user is
 *       about to commit to.</li>
 *   <li>{@link #confirmPassword} — the duplicate of the new password.</li>
 * </ul>
 * <p>The auto-generated record {@code toString()} is therefore
 * overridden to redact all three fields. Equality and hashing remain
 * unchanged so unit tests can still assert structural equivalence.</p>
 *
 * @param token           raw reset token from the email link;
 *                        stripped by the canonical constructor;
 *                        never logged
 * @param password        new raw password; hashed with BCrypt before
 *                        storage; never logged
 * @param confirmPassword must equal {@link #password}; checked in
 *                        {@code AuthService}; never logged
 */
@JsonPropertyOrder({ "token", "password", "confirmPassword" })
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

    /**
     * Defensive canonical constructor: strips surrounding whitespace
     * from the token so {@code AuthService.resetPassword} receives the
     * canonical form, and leaves password fields byte-identical so
     * BCrypt hashes the exact value the user typed. {@code null} is
     * preserved for any field so {@link NotBlank @NotBlank} renders
     * the user-facing "is required" message rather than a low-level
     * {@link NullPointerException}.
     */
    public ResetPasswordRequest {
        if (token != null) {
            token = token.strip();
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Password and confirm password must be at most 72 bytes when encoded as UTF-8")
    public boolean isPasswordsWithinBcryptByteLimit() {
        return AuthDtoValidation.isWithinBcryptByteLimit(password)
                && AuthDtoValidation.isWithinBcryptByteLimit(confirmPassword);
    }

    /**
     * Redacted {@code toString()} so neither the single-use reset
     * token nor the plaintext password / confirmation can escape
     * through log lines, exception messages or debug dumps. All three
     * fields are credentials within the request's lifetime and must
     * be treated as opaque secrets by every observer of this object.
     */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***REDACTED***"
                + ", password=***REDACTED***"
                + ", confirmPassword=***REDACTED***]";
    }
}
