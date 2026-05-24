package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Single row in the admin "all students" list.
 *
 * <p>Carries the minimum fields the admin students table renders —
 * identity + locale + signup time. Full student detail (XP totals,
 * level progression, badge history, recent activity, etc.) lives on a
 * dedicated student-detail endpoint that is fetched only when an
 * admin clicks through.</p>
 *
 * <p>Both {@link #studentId} (the {@code StudentProfile} id) and
 * {@link #userId} (the {@code User} id) are exposed so the admin UI
 * can deep-link to either the learning dashboard view or the auth /
 * commerce views without an extra round trip.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #studentId}, {@link #userId}, {@link #fullName},
 *       {@link #email} and {@link #createdAt} are non-{@code null} —
 *       these are the columns the admin table relies on to render and
 *       sort every row.</li>
 *   <li>{@link #preferredLanguage} may be {@code null} for legacy
 *       accounts that pre-date the field.</li>
 * </ul>
 *
 * @param studentId         {@code StudentProfile} identifier;
 *                          never {@code null}
 * @param userId            {@code User} identifier; never {@code null}
 * @param fullName          display name on the user record;
 *                          never {@code null}
 * @param email             lower-cased login email; never {@code null}
 * @param preferredLanguage UI locale ({@code en}, {@code ms},
 *                          {@code zh}); may be {@code null} for legacy
 *                          accounts that pre-date the field
 * @param createdAt         when the student account was created;
 *                          never {@code null}
 */
@JsonPropertyOrder({
        "studentId",
        "userId",
        "fullName",
        "email",
        "preferredLanguage",
        "createdAt"
})
public record AdminStudentListItemResponse(
        Long studentId,
        Long userId,
        String fullName,
        String email,
        String preferredLanguage,
        LocalDateTime createdAt) {

    /**
     * Defensive canonical constructor: enforces non-null on every
     * field the admin students table renders unconditionally.
     * {@link #preferredLanguage} is intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public AdminStudentListItemResponse {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
