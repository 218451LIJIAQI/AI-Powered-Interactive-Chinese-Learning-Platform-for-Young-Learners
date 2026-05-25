package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload submitted to
 * {@code POST /api/v1/auth/register/teacher}.
 *
 * <p>A successful request creates a {@code User} with the
 * {@code TEACHER} role <em>and</em> a companion {@code TeacherProfile}
 * row in
 * {@link com.example.aichinese.entity.enums.TeacherStatus#PENDING}.
 * The teacher cannot publish content or be surfaced in the public
 * catalogue until an administrator approves the verification — this
 * is the cornerstone of the platform's trust model. The teacher
 * onboarding UI then prompts the user to upload supporting documents
 * via {@code POST /api/v1/teacher/me/verification-documents}.</p>
 *
 * <p>Password / email validation rules and rationale match
 * {@link RegisterStudentRequest}; see that record's Javadoc for the
 * BCrypt 72-byte cap, the password / confirmPassword asymmetry, and
 * the email normalisation contract.</p>
 *
 * <p>{@link #bio} is the teacher's public-profile blurb shown in the
 * catalogue once they are approved. It is optional at registration
 * time so a teacher can sign up first and complete the public profile
 * later from the teacher dashboard.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#strip() strips}
 * {@link #fullName}, {@link #email} and {@link #bio} so Bean
 * Validation operates on the canonical form and downstream
 * {@code AuthService} normalisation steps remain idempotent. Password
 * fields are intentionally <em>not</em> stripped: leading or trailing
 * spaces are significant characters bound directly to the BCrypt
 * hash.</p>
 *
 * <h2>Security</h2>
 * <p>The auto-generated record {@code toString()} is overridden to
 * redact both password fields — accidentally printing a
 * {@code RegisterTeacherRequest} to a log file, exception trace, or
 * debug console must never leak the user's plaintext credentials.
 * Equality and hashing intentionally remain unchanged so unit tests
 * can still assert structural equivalence.</p>
 *
 * @param fullName        display name used across teacher-facing UI;
 *                        stripped by the canonical constructor
 * @param email           login identifier; stripped by the canonical
 *                        constructor and lower-cased server-side
 * @param password        raw password; hashed with BCrypt before
 *                        storage; never logged
 * @param confirmPassword must equal {@link #password}; checked in
 *                        {@code AuthService}; never logged
 * @param bio             optional public-profile blurb (max 2000 chars);
 *                        stripped by the canonical constructor
 */
@JsonPropertyOrder({ "fullName", "email", "password", "confirmPassword", "bio" })
public record RegisterTeacherRequest(
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
        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio) {

    /**
     * Defensive canonical constructor: strips user-typed text fields so
     * Bean Validation operates on the canonical form, and leaves
     * password fields byte-identical so BCrypt hashes the exact value
     * the user typed. {@code null} is preserved for any field so the
     * {@link NotBlank @NotBlank} validators render their friendly
     * user-facing messages rather than a low-level
     * {@link NullPointerException}.
     */
    public RegisterTeacherRequest {
        if (fullName != null) {
            fullName = fullName.strip();
        }
        if (email != null) {
            email = email.strip();
        }
        if (bio != null) {
            bio = bio.strip();
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
     * {@code bio}) are preserved so audit / forensic correlation still
     * works — these are accepted PII surface captured by
     * {@code AuditLogService.sanitize}.
     */
    @Override
    public String toString() {
        return "RegisterTeacherRequest[fullName=" + fullName
                + ", email=" + email
                + ", password=***REDACTED***"
                + ", confirmPassword=***REDACTED***"
                + ", bio=" + bio + "]";
    }
}
