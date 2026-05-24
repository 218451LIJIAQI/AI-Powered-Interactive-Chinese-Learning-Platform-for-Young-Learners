package com.example.aichinese.dto.auth;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #fullName}, {@link #email} and
 *       {@link #preferredLanguage} are non-{@code null} and non-blank.</li>
 *   <li>{@link #roles} is non-{@code null} and defensively copied; the
 *       embedded list is unmodifiable so an upstream mutation cannot
 *       leak into an already-serialised payload.</li>
 *   <li>{@link #studentProfile} and {@link #teacherProfile} may be
 *       {@code null} per the role rules above.</li>
 * </ul>
 *
 * @param id                opaque user identifier; never {@code null}
 * @param fullName          display name on the {@code User} record;
 *                          never {@code null} / blank
 * @param email             lower-cased login email; never {@code null}
 *                          / blank
 * @param preferredLanguage UI locale ({@code en}, {@code ms}, or
 *                          {@code zh}); never {@code null} / blank
 *                          after the service layer's defaulting step
 * @param roles             Spring Security authority strings prefixed
 *                          with {@code ROLE_}; never {@code null}
 * @param studentProfile    student-extension data, or {@code null} for
 *                          non-student accounts
 * @param teacherProfile    teacher-extension data, or {@code null} for
 *                          non-teacher accounts
 */
@JsonPropertyOrder({
        "id",
        "fullName",
        "email",
        "preferredLanguage",
        "roles",
        "studentProfile",
        "teacherProfile"
})
public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        StudentProfileSummary studentProfile,
        TeacherProfileSummary teacherProfile) {

    /**
     * Defensive canonical constructor: enforces the identity-field
     * invariants the frontend's role-aware shell relies on, and
     * defensively copies {@link #roles} so external mutation cannot
     * leak into the already-serialised payload.
     */
    public CurrentUserResponse {
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

    /**
     * Student-specific profile snapshot embedded in
     * {@link CurrentUserResponse}.
     *
     * <p>{@link #displayName}, {@link #currentLevel} and
     * {@link #totalPoints} are intentionally tolerant of {@code null}:
     * {@code displayName} may be blank for legacy student rows and the
     * frontend falls back to the parent {@code User.fullName};
     * {@code currentLevel} is {@code null} when no learning level has
     * been assigned yet; {@code totalPoints} is denormalised from the
     * {@code points_transactions} ledger and may be {@code null} on
     * profiles that have never earned XP.</p>
     *
     * @param id            opaque student-profile identifier; never {@code null}
     * @param displayName   student-chosen friendly nickname (falls back
     *                      to the user's {@code fullName} when blank)
     * @param currentLevel  human-readable current
     *                      {@code LearningLevel.name} (e.g. "HSK 1");
     *                      {@code null} when no level is assigned yet
     * @param totalPoints   denormalised XP total — the canonical
     *                      source is the
     *                      {@code points_transactions} ledger
     */
    @JsonPropertyOrder({ "id", "displayName", "currentLevel", "totalPoints" })
    public record StudentProfileSummary(
            Long id,
            String displayName,
            String currentLevel,
            Integer totalPoints) {

        public StudentProfileSummary {
            Objects.requireNonNull(id, "id must not be null");
        }
    }

    /**
     * Teacher-specific profile snapshot embedded in
     * {@link CurrentUserResponse}.
     *
     * @param id              opaque teacher-profile identifier;
     *                        never {@code null}
     * @param teacherStatus   verification state; never {@code null} —
     *                        only {@link TeacherStatus#APPROVED}
     *                        teachers may publish content
     * @param bio             public-profile blurb (may be {@code null})
     * @param rejectionReason populated by an admin when the most
     *                        recent verification review was
     *                        {@link TeacherStatus#REJECTED}; surfaced
     *                        in the teacher UI so they know what to
     *                        fix on resubmit
     */
    @JsonPropertyOrder({ "id", "teacherStatus", "bio", "rejectionReason" })
    public record TeacherProfileSummary(
            Long id,
            TeacherStatus teacherStatus,
            String bio,
            String rejectionReason) {

        public TeacherProfileSummary {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        }
    }
}
