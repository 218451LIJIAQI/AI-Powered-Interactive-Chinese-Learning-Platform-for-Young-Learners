package com.example.aichinese.dto.auth;

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
 * @param fullName          display name used across student-facing UI
 * @param email             login identifier; lower-cased server-side
 * @param password          raw password; hashed with BCrypt before storage
 * @param confirmPassword   must equal {@link #password}; checked in
 *                          {@code AuthService}
 * @param preferredLanguage UI locale hint; empty/null defaults to
 *                          {@code en}
 */
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
}
