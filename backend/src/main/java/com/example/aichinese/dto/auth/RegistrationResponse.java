package com.example.aichinese.dto.auth;

import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Confirmation payload returned by both
 * {@code POST /api/v1/auth/register/student} and
 * {@code POST /api/v1/auth/register/teacher}.
 *
 * <p>Registration intentionally does <em>not</em> issue a JWT — the
 * frontend redirects the new user to the login screen and asks them
 * to authenticate explicitly. This separation:</p>
 * <ul>
 *   <li>Forces the new user to verify they remember the password they
 *       just typed (catches typos in the registration form).</li>
 *   <li>Keeps the registration endpoint stateless — no token lifecycle
 *       to manage if registration fails downstream.</li>
 *   <li>Lets the frontend reuse the standard login flow, including
 *       its rate limiter and lockout handling.</li>
 * </ul>
 *
 * <p>{@link #teacherStatus} is populated as
 * {@link TeacherStatus#PENDING} for teacher registrations (a hint that
 * the teacher dashboard should immediately surface the verification
 * upload step) and {@code null} for student registrations.</p>
 *
 * @param id                newly assigned user identifier
 * @param fullName          display name as stored on the user record
 * @param email             lower-cased login email
 * @param preferredLanguage UI locale defaulted from the request
 * @param roles             Spring Security authority strings prefixed
 *                          with {@code ROLE_} (a single-element list
 *                          in current registration flows)
 * @param teacherStatus     {@code PENDING} for teacher registrations,
 *                          {@code null} for students
 */
public record RegistrationResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        TeacherStatus teacherStatus) {
}
