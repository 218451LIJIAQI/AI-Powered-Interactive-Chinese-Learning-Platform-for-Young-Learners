package com.example.aichinese.dto.auth;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Successful login response returned by {@code POST /api/v1/auth/login}.
 *
 * <p>The {@link #token} is the JWT bearer token the client must include
 * as {@code Authorization: Bearer <token>} on every subsequent
 * authenticated request. {@link #user} is a denormalised snapshot of
 * the just-authenticated user so the frontend can render the
 * role-aware shell immediately without a follow-up
 * {@code GET /api/v1/auth/me} call. (The companion
 * {@link CurrentUserResponse} returned by {@code /me} carries the
 * fuller profile-extension data.)</p>
 *
 * <h2>Security</h2>
 * <p>The auto-generated record {@code toString()} is overridden to
 * redact the JWT — accidentally printing an {@code AuthResponse} to a
 * log file, exception trace, or debug console must never leak a usable
 * bearer token. Equality and hashing intentionally remain unchanged so
 * tests can still assert structural equivalence.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #token} is non-{@code null} and non-blank.</li>
 *   <li>{@link #user} is non-{@code null}.</li>
 * </ul>
 *
 * @param token JWT bearer token; opaque to the frontend; never
 *              {@code null} / blank
 * @param user  minimal user summary suitable for first-paint UI;
 *              never {@code null}
 */
@JsonPropertyOrder({ "token", "user" })
public record AuthResponse(
        String token,
        UserSummary user) {

    /**
     * Defensive canonical constructor: rejects a {@code null} / blank
     * {@code token} and {@code null} {@code user}. A bug in the auth
     * pipeline that produces an empty token would otherwise hand the
     * frontend an unauthenticated session, so failing fast here is
     * preferable to silent corruption.
     */
    public AuthResponse {
        Objects.requireNonNull(token, "token must not be null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        Objects.requireNonNull(user, "user must not be null");
    }

    /**
     * Redacted {@code toString()} so the JWT never escapes through log
     * lines, exception messages or debug dumps. Keeps the user summary
     * visible so forensic correlation by user id / email is still
     * possible.
     */
    @Override
    public String toString() {
        return "AuthResponse[token=***REDACTED***, user=" + user + "]";
    }

    /**
     * Minimal user identity included alongside the JWT in the login
     * response. Fields are limited to what the frontend needs to render
     * the navigation shell:
     *
     * <ul>
     *   <li>{@link #id} — for client-side cache keys</li>
     *   <li>{@link #fullName} — header greeting</li>
     *   <li>{@link #email} — settings panel</li>
     *   <li>{@link #roles} — role-aware menu (e.g. {@code ROLE_STUDENT})</li>
     *   <li>{@link #teacherStatus} — when the user is a teacher, signals
     *       whether they may navigate into the publishing flows or are
     *       still pending verification</li>
     * </ul>
     *
     * <p>{@link #teacherStatus} is {@code null} for non-teacher users.
     * {@link #roles} is never {@code null}; a {@code null} argument is
     * normalised to {@link List#of()} and the supplied list is
     * defensively copied so external mutation cannot leak into the
     * already-serialised payload.</p>
     *
     * @param id            opaque user identifier; never {@code null}
     * @param fullName      display name as set on the user record;
     *                      never {@code null} / blank
     * @param email         the email used to authenticate (lower-cased);
     *                      never {@code null} / blank
     * @param roles         Spring Security authority strings prefixed
     *                      with {@code ROLE_} (e.g. {@code ROLE_TEACHER});
     *                      never {@code null}
     * @param teacherStatus the teacher's verification state, or
     *                      {@code null} for non-teacher accounts
     */
    @JsonPropertyOrder({ "id", "fullName", "email", "roles", "teacherStatus" })
    public record UserSummary(
            Long id,
            String fullName,
            String email,
            List<String> roles,
            TeacherStatus teacherStatus) {

        /**
         * Defensive canonical constructor: rejects {@code null} /
         * blank values for the identity fields the frontend relies on
         * to render the navigation shell, and copies {@code roles}
         * defensively so an upstream mutation cannot mutate the
         * already-serialised payload.
         */
        public UserSummary {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(fullName, "fullName must not be null");
            if (fullName.isBlank()) {
                throw new IllegalArgumentException("fullName must not be blank");
            }
            Objects.requireNonNull(email, "email must not be null");
            if (email.isBlank()) {
                throw new IllegalArgumentException("email must not be blank");
            }
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
