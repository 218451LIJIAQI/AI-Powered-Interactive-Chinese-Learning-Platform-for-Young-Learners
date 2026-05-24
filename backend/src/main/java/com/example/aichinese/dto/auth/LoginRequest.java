package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials submitted to {@code POST /api/v1/auth/login}.
 *
 * <p>Validation only enforces shape (non-blank, well-formed email,
 * length caps); the actual authentication outcome is decided by
 * {@code AuthenticationManager} in {@code AuthService} and reported
 * back as a {@code 401 Unauthorized} when the credentials do not match,
 * or as a structured {@code 429 Too Many Requests} when
 * {@code LoginAttemptLimiter} has locked the account out.</p>
 *
 * <p><b>Why the 72-character password cap.</b> BCrypt — the password
 * hash function used by {@code passwordEncoder} — silently truncates
 * inputs to 72 bytes. Capping the request at the same length ensures
 * the {@code @NotBlank} validator and the hash function agree, and
 * prevents users from believing their long passphrase is more secure
 * than the actually-hashed prefix.</p>
 *
 * <p><b>Why the 320-character email cap.</b> RFC 5321 §4.5.3.1 limits
 * the {@code local@domain} pair to 320 octets total. Anything longer
 * is non-deliverable in practice.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims} the
 * email so Bean Validation sees the canonical form and downstream
 * {@code AuthService.normalizeEmail} (trim + lowercase) is idempotent.
 * The password is intentionally <em>not</em> trimmed: leading or
 * trailing spaces in a password are significant characters and the
 * BCrypt hash binds to the exact byte sequence.</p>
 *
 * <h2>Security</h2>
 * <p>The auto-generated record {@code toString()} is overridden to
 * redact the password — accidentally printing a {@code LoginRequest}
 * to a log file, exception trace, or debug console must never leak
 * the user's plaintext credentials. Equality and hashing intentionally
 * remain unchanged so unit tests can still assert structural
 * equivalence (e.g. when verifying that a deserialiser reproduces an
 * expected payload).</p>
 *
 * @param email    user-facing login identifier; trimmed by the
 *                 canonical constructor and case-normalised at the
 *                 service layer
 * @param password raw password; never stored, never logged — hashed
 *                 with BCrypt by the authentication manager
 */
@JsonPropertyOrder({ "email", "password" })
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password) {

    /**
     * Defensive canonical constructor: trims surrounding whitespace
     * from the email so Bean Validation operates on the canonical
     * form, and leaves the password byte-identical so BCrypt hashes
     * the exact value the user typed. {@code null} is preserved for
     * either field so {@link NotBlank @NotBlank} renders the
     * user-facing "is required" message rather than a low-level
     * {@link NullPointerException}.
     */
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }

    /**
     * Redacted {@code toString()} so the plaintext password never
     * escapes through log lines, exception messages or debug dumps.
     * The email is intentionally preserved so audit / forensic
     * correlation still works — leaking an email is an accepted PII
     * trade-off captured by {@code AuditLogService.sanitize}, but
     * leaking a password is not.
     */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***REDACTED***]";
    }
}
