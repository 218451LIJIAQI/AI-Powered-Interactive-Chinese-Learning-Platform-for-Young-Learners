package com.example.aichinese.dto.auth;

import java.util.List;

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
 * @param token JWT bearer token; opaque to the frontend
 * @param user  minimal user summary suitable for first-paint UI
 */
public record AuthResponse(
        String token,
        UserSummary user) {

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
     * <p>{@link #teacherStatus} is {@code null} for non-teacher users.</p>
     *
     * @param id            opaque user identifier
     * @param fullName      display name as set on the user record
     * @param email         the email used to authenticate (lower-cased)
     * @param roles         Spring Security authority strings prefixed
     *                      with {@code ROLE_} (e.g. {@code ROLE_TEACHER})
     * @param teacherStatus the teacher's verification state, or
     *                      {@code null} for non-teacher accounts
     */
    public record UserSummary(
            Long id,
            String fullName,
            String email,
            List<String> roles,
            TeacherStatus teacherStatus) {
    }
}
