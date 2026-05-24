package com.example.aichinese.dto.content;

/**
 * Public-facing teacher reference embedded inside
 * {@link PublicCourseSummaryResponse#teacher()} and
 * {@link PublicCourseDetailResponse#teacher()}.
 *
 * <p>Strictly leaner than {@link TeacherOwnerSummaryResponse}:</p>
 * <ul>
 *   <li>Drops {@code userId} — un-authenticated visitors cannot
 *       message teachers, so the user-id linkage is irrelevant.</li>
 *   <li>Replaces the full
 *       {@link com.example.aichinese.entity.enums.TeacherStatus}
 *       enum with a single {@link #verified} boolean — the public
 *       catalogue only surfaces approved teachers anyway, so the
 *       three-state nuance is meaningless to a learner; the flag
 *       drives a "✓ Verified teacher" badge.</li>
 *   <li>Renames {@code fullName} to the more accurate
 *       {@link #displayName} since the value is
 *       {@code users.display_name}.</li>
 * </ul>
 *
 * <p>{@link #teacherId} is preserved so the front-end can deep-link
 * to {@code GET /api/v1/public/teachers/{id}} for the full teacher
 * profile page.</p>
 *
 * <p>Primitive {@code boolean} for {@link #verified} so the JSON
 * wire format always emits a value.</p>
 */
public record PublicTeacherSummaryResponse(
        Long teacherId,
        String displayName,
        boolean verified) {
}
