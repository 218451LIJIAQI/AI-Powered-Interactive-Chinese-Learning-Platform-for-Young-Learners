package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>All three fields are non-{@code null} — when the producer
 *       constructs this DTO it has just dereferenced both a real
 *       {@link com.example.aichinese.entity.TeacherProfile} and the
 *       linked {@link com.example.aichinese.entity.User}; the
 *       schema guarantees the columns are populated. The producer
 *       passes {@code null} as the whole DTO (rather than as
 *       individual fields) when either entity is missing.</li>
 * </ul>
 *
 * @param teacherId   {@code teacher_profiles.id} used for "Teacher
 *                    detail" deep-links; never {@code null}
 * @param userId      {@code users.id} used for messaging /
 *                    follow-up; never {@code null}
 * @param displayName denormalised teacher display name; never
 *                    {@code null}
 */
@JsonPropertyOrder({ "teacherId", "userId", "displayName" })
public record LiveClassTeacherSummaryResponse(
        Long teacherId,
        Long userId,
        String displayName) {

    /**
     * Defensive canonical constructor: enforces non-null on all
     * three fields per the invariants documented above. The
     * producer never partially-populates this DTO — it either
     * constructs the full triple or omits the embedded teacher
     * summary entirely.
     */
    public LiveClassTeacherSummaryResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
    }
}
