package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Single row in the admin "all users" list — students, teachers, and
 * fellow administrators all surface here.
 *
 * <p>The list is the entry point for cross-role administration:
 * deactivating a misbehaving user, resetting a stuck account, or
 * inspecting historical activity. Role-specific drill-down DTOs
 * ({@link AdminStudentListItemResponse},
 * {@link AdminTeacherDetailResponse}) carry richer fields when an
 * admin clicks into a specific user.</p>
 *
 * <p>{@link #active} reflects {@code User.active}: {@code false}
 * accounts cannot log in (handled at the authentication filter) but
 * remain visible to the admin so they can be re-enabled.
 * {@link #teacherStatus} is non-null only for teacher accounts so the
 * UI can render a single combined "status" column.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #userId}, {@link #fullName}, {@link #email},
 *       {@link #createdAt} and {@link #updatedAt} are non-{@code null}
 *       — these are the columns the admin users table renders
 *       unconditionally.</li>
 *   <li>{@link #roles} is non-{@code null} (empty list when no roles
 *       are assigned, though that should never happen for a real
 *       {@code User}) and defensively copied so external mutation
 *       cannot leak into the already-serialised payload.</li>
 *   <li>{@link #teacherStatus} may be {@code null} for non-teacher
 *       accounts.</li>
 * </ul>
 *
 * @param userId        {@code User} identifier; never {@code null}
 * @param fullName      display name on the user record;
 *                      never {@code null}
 * @param email         lower-cased login email; never {@code null}
 * @param roles         Spring Security authority strings (e.g.
 *                      {@code ROLE_STUDENT}, {@code ROLE_TEACHER},
 *                      {@code ROLE_ADMIN}); never {@code null}
 * @param active        {@code true} when the user can authenticate
 * @param teacherStatus verification state when the user is a teacher;
 *                      {@code null} otherwise
 * @param createdAt     when the user account was created;
 *                      never {@code null}
 * @param updatedAt     when the user record was last modified;
 *                      never {@code null}
 */
@JsonPropertyOrder({
        "userId",
        "fullName",
        "email",
        "roles",
        "active",
        "teacherStatus",
        "createdAt",
        "updatedAt"
})
public record AdminUserListItemResponse(
        Long userId,
        String fullName,
        String email,
        List<String> roles,
        boolean active,
        TeacherStatus teacherStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / timestamp fields the admin users table renders
     * unconditionally, and defensively copies {@link #roles}
     * (with {@code null} → {@link List#of()} normalisation) so
     * external mutation cannot leak into the already-serialised
     * payload. {@link #teacherStatus} is intentionally permitted to
     * be {@code null} for non-teacher accounts.
     */
    public AdminUserListItemResponse {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
