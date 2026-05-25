package com.example.aichinese.dto.auth;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload submitted to
 * {@code POST /api/v1/auth/register/student}.
 *
 * <p>The student-flow registration is open to anonymous traffic;
 * abuse is bounded by {@code PublicAuthRequestLimiter}. Successful
 * requests create a new {@code User} with the {@code STUDENT} role
 * and return a {@link RegistrationResponse} embedded inside the
 * standard {@link com.example.aichinese.dto.ApiResponse} envelope.</p>
 *
 * <h2>Why password and confirmPassword have asymmetric size rules</h2>
 * <p>{@link #password} carries the {@code min = 8} cap because that is
 * the project's complexity floor. {@link #confirmPassword} only enforces
 * {@code max = 72} — a {@code min = 8} would produce a confusing
 * "Confirm password must be 8 chars" message when the user simply
 * mistyped a long password into the second field. The actual equality
 * check (and therefore implicit min-length agreement) lives in
 * {@code AuthService.validatePasswords} so the response message reads
 * "Confirm password does not match." in that scenario.</p>
 *
 * <p>The 72-byte cap reflects BCrypt's intrinsic input limit; longer
 * inputs would be silently truncated by the hash function.</p>
 *
 * <h2>preferredLanguage</h2>
 * <p>{@link #preferredLanguage} is restricted to {@code en} (English),
 * {@code ms} (Bahasa Melayu), or {@code zh} (Chinese). The empty
 * string and {@code null} are also accepted — both default to
 * {@code en} at the service layer through
 * {@code AuthService.normalizePreferredLanguage}.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#strip() strips}
 * {@link #fullName}, {@link #email} and {@link #preferredLanguage} so
 * Bean Validation operates on the canonical form and downstream
 * {@code AuthService} normalisation steps remain idempotent. Password
 * fields are intentionally <em>not</em> stripped: leading or trailing
 * spaces are significant characters bound directly to the BCrypt
 * hash.</p>
 *
 * <h2>Security</h2>
 * <p>The auto-generated record {@code toString()} is overridden to
 * redact both password fields — accidentally printing a
 * {@code RegisterStudentRequest} to a log file, exception trace, or
 * debug console must never leak the user's plaintext credentials.
 * Equality and hashing intentionally remain unchanged so unit tests
 * can still assert structural equivalence.</p>
 *
 * @param fullName          display name used across student-facing UI;
 *                          stripped by the canonical constructor
 * @param email             login identifier; stripped by the canonical
 *                          constructor and lower-cased server-side
 * @param password          raw password; hashed with BCrypt before
 *                          storage; never logged
 * @param confirmPassword   must equal {@link #password}; checked in
 *                          {@code AuthService}; never logged
 * @param preferredLanguage UI locale hint; stripped by the canonical
 *                          constructor; empty/null defaults to
 *                          {@code en}
 */
@JsonPropertyOrder({ "fullName", "email", "password", "confirmPassword", "preferredLanguage" })
public record RegisterStudentRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
        @NotBlank(message = "Confirm password is required")
        @Size(max = 72, message = "Confirm password must be at most 72 characters")
        String confirmPassword,
        @Pattern(regexp = "^(en|ms|zh)?$", message = "Preferred language must be one of en, ms, or zh")
        @Size(max = 10, message = "Preferred language must be at most 10 characters")
        String preferredLanguage) {

    /**
     * Defensive canonical constructor: strips user-typed text fields so
     * Bean Validation operates on the canonical form, and leaves
     * password fields byte-identical so BCrypt hashes the exact value
     * the user typed. {@code null} is preserved for any field so the
     * {@link NotBlank @NotBlank} / {@link Pattern @Pattern} validators
     * render their friendly user-facing messages rather than a
     * low-level {@link NullPointerException}.
     */
    public RegisterStudentRequest {
        if (fullName != null) {
            fullName = fullName.strip();
        }
        if (email != null) {
            email = email.strip();
        }
        if (preferredLanguage != null) {
            preferredLanguage = preferredLanguage.strip().toLowerCase(Locale.ROOT);
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Password and confirm password must be at most 72 bytes when encoded as UTF-8")
    public boolean isPasswordsWithinBcryptByteLimit() {
        return AuthDtoValidation.isWithinBcryptByteLimit(password)
                && AuthDtoValidation.isWithinBcryptByteLimit(confirmPassword);
    }

    /**
     * Redacted {@code toString()} so the plaintext password and its
     * confirmation never escape through log lines, exception messages
     * or debug dumps. Identity fields ({@code fullName}, {@code email},
     * {@code preferredLanguage}) are preserved so audit / forensic
     * correlation still works — these are accepted PII surface
     * captured by {@code AuditLogService.sanitize}.
     */
    @Override
    public String toString() {
        return "RegisterStudentRequest[fullName=" + fullName
                + ", email=" + email
                + ", password=***REDACTED***"
                + ", confirmPassword=***REDACTED***"
                + ", preferredLanguage=" + preferredLanguage + "]";
    }
}
