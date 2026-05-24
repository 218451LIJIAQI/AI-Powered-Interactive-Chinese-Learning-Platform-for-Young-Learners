package com.example.aichinese.dto.auth;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #fullName}, {@link #email} and
 *       {@link #preferredLanguage} are non-{@code null} and non-blank.</li>
 *   <li>{@link #roles} is non-{@code null} and defensively copied; the
 *       embedded list is unmodifiable so an upstream mutation cannot
 *       leak into an already-serialised payload.</li>
 *   <li>{@link #teacherStatus} may be {@code null} per the role rules
 *       above.</li>
 * </ul>
 *
 * @param id                newly assigned user identifier; never
 *                          {@code null}
 * @param fullName          display name as stored on the user record;
 *                          never {@code null} / blank
 * @param email             lower-cased login email; never {@code null}
 *                          / blank
 * @param preferredLanguage UI locale defaulted from the request;
 *                          never {@code null} / blank
 * @param roles             Spring Security authority strings prefixed
 *                          with {@code ROLE_} (a single-element list
 *                          in current registration flows); never
 *                          {@code null}
 * @param teacherStatus     {@code PENDING} for teacher registrations,
 *                          {@code null} for students
 */
@JsonPropertyOrder({
        "id",
        "fullName",
        "email",
        "preferredLanguage",
        "roles",
        "teacherStatus"
})
public record RegistrationResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        TeacherStatus teacherStatus) {

    /**
     * Defensive canonical constructor: enforces the identity-field
     * invariants the frontend's redirect-to-login flow relies on, and
     * defensively copies {@link #roles} so external mutation cannot
     * leak into the already-serialised payload.
     */
    public RegistrationResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        Objects.requireNonNull(email, "email must not be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        Objects.requireNonNull(preferredLanguage, "preferredLanguage must not be null");
        if (preferredLanguage.isBlank()) {
            throw new IllegalArgumentException("preferredLanguage must not be blank");
        }
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
