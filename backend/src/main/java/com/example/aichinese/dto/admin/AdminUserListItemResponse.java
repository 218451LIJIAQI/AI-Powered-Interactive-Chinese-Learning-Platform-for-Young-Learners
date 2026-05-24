package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

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
 * @param userId        {@code User} identifier
 * @param fullName      display name on the user record
 * @param email         lower-cased login email
 * @param roles         Spring Security authority strings (e.g.
 *                      {@code ROLE_STUDENT}, {@code ROLE_TEACHER},
 *                      {@code ROLE_ADMIN}); never {@code null}
 * @param active        {@code true} when the user can authenticate
 * @param teacherStatus verification state when the user is a teacher;
 *                      {@code null} otherwise
 * @param createdAt     when the user account was created
 * @param updatedAt     when the user record was last modified
 */
public record AdminUserListItemResponse(
        Long userId,
        String fullName,
        String email,
        List<String> roles,
        boolean active,
        TeacherStatus teacherStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
