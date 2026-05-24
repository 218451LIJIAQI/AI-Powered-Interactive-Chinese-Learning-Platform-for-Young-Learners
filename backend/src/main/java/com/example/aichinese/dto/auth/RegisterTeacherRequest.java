package com.example.aichinese.dto.auth;

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
 * @param fullName        display name used across teacher-facing UI
 * @param email           login identifier; lower-cased server-side
 * @param password        raw password; hashed with BCrypt before storage
 * @param confirmPassword must equal {@link #password}; checked in
 *                        {@code AuthService}
 * @param bio             optional public-profile blurb (max 2000 chars)
 */
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
}
