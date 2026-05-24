package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;

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
 * @param studentId         {@code StudentProfile} identifier
 * @param userId            {@code User} identifier
 * @param fullName          display name on the user record
 * @param email             lower-cased login email
 * @param preferredLanguage UI locale ({@code en}, {@code ms},
 *                          {@code zh}); may be {@code null} for legacy
 *                          accounts that pre-date the field
 * @param createdAt         when the student account was created
 */
public record AdminStudentListItemResponse(
        Long studentId,
        Long userId,
        String fullName,
        String email,
        String preferredLanguage,
        LocalDateTime createdAt) {
}
