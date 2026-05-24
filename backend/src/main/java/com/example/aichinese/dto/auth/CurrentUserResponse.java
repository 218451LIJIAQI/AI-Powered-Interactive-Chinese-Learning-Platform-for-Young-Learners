package com.example.aichinese.dto.auth;

import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Full "who am I" payload returned by
 * {@code GET /api/v1/auth/me}.
 *
 * <p>Whereas the login response embeds a minimal
 * {@link AuthResponse.UserSummary} for first-paint, this record carries
 * the role-specific profile extensions ({@link #studentProfile} or
 * {@link #teacherProfile}) so the dashboard can render personalised
 * widgets without further API calls.</p>
 *
 * <p>The {@code /me} response is served with {@code Cache-Control:
 * no-store} so a logged-out user pressing the browser back button
 * cannot replay a stale authenticated profile from the bfcache.</p>
 *
 * <p>Exactly one of {@link #studentProfile} / {@link #teacherProfile}
 * is non-null for end-user accounts; both are {@code null} for admin
 * users (admins do not have a learner-style profile extension).</p>
 *
 * @param id                opaque user identifier
 * @param fullName          display name on the {@code User} record
 * @param email             lower-cased login email
 * @param preferredLanguage UI locale ({@code en}, {@code ms}, or
 *                          {@code zh}); never {@code null} after the
 *                          service layer's defaulting step
 * @param roles             Spring Security authority strings prefixed
 *                          with {@code ROLE_}
 * @param studentProfile    student-extension data, or {@code null} for
 *                          non-student accounts
 * @param teacherProfile    teacher-extension data, or {@code null} for
 *                          non-teacher accounts
 */
public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        StudentProfileSummary studentProfile,
        TeacherProfileSummary teacherProfile) {

    /**
     * Student-specific profile snapshot embedded in
     * {@link CurrentUserResponse}.
     *
     * @param id            opaque student-profile identifier
     * @param displayName   student-chosen friendly nickname (falls back
     *                      to the user's {@code fullName} when blank)
     * @param currentLevel  human-readable current
     *                      {@code LearningLevel.name} (e.g. "HSK 1");
     *                      {@code null} when no level is assigned yet
     * @param totalPoints   denormalised XP total — the canonical
     *                      source is the
     *                      {@code points_transactions} ledger
     */
    public record StudentProfileSummary(
            Long id,
            String displayName,
            String currentLevel,
            Integer totalPoints) {
    }

    /**
     * Teacher-specific profile snapshot embedded in
     * {@link CurrentUserResponse}.
     *
     * @param id              opaque teacher-profile identifier
     * @param teacherStatus   verification state; only {@code APPROVED}
     *                        teachers may publish content
     * @param bio             public-profile blurb (may be {@code null})
     * @param rejectionReason populated by an admin when the most
     *                        recent verification review was
     *                        {@code REJECTED}; surfaced in the teacher
     *                        UI so they know what to fix on resubmit
     */
    public record TeacherProfileSummary(
            Long id,
            TeacherStatus teacherStatus,
            String bio,
            String rejectionReason) {
    }
}
