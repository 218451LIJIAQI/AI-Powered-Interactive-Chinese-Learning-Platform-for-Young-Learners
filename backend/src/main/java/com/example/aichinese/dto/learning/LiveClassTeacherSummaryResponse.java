package com.example.aichinese.dto.learning;

/**
 * Compact teacher reference embedded inside
 * {@link LiveClassResponse#teacher()}.
 *
 * <p>Carries the minimum identity the student-facing card needs to
 * show "Taught by Dr. Chen" with a click-through to the public
 * teacher profile. Both id columns are surfaced because the
 * front-end navigation distinguishes {@link #teacherId}
 * (the {@code teacher_profiles.id} used for "Teacher detail" routes)
 * from {@link #userId} (the {@code users.id} used for messaging).</p>
 *
 * <p>{@link #displayName} is denormalised from
 * {@code users.display_name} to avoid an extra join in the live
 * class list; never blank for an active teacher.</p>
 */
public record LiveClassTeacherSummaryResponse(
        Long teacherId,
        Long userId,
        String displayName) {
}
