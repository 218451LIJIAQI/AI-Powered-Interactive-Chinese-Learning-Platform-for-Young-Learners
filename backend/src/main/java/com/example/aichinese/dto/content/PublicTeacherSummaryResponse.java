package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *       drives a verified-teacher badge on the catalogue card.</li>
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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on {@link #teacherId}
 * because the mapper only emits this DTO when the underlying
 * {@code TeacherProfile} and its {@code user} association both
 * hydrated (see
 * {@link com.example.aichinese.mapper.ContentManagementMapper#toPublicTeacherSummary}).
 * {@link #displayName} may legitimately be {@code null} when the
 * teacher has not yet set a display name on their user row — the
 * front-end falls back to a generic "Teacher" label in that case.</p>
 *
 * @param teacherId   {@code teacher_profiles.id}; never
 *                    {@code null}
 * @param displayName denormalised {@code users.display_name};
 *                    nullable when the teacher has not chosen one
 * @param verified    {@code true} when the underlying
 *                    {@link com.example.aichinese.entity.enums.TeacherStatus}
 *                    is
 *                    {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED};
 *                    primitive so the wire format always emits a
 *                    value
 */
@JsonPropertyOrder({ "teacherId", "displayName", "verified" })
public record PublicTeacherSummaryResponse(
        Long teacherId,
        String displayName,
        boolean verified) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * field the mapper guarantees from any persisted teacher
     * profile so a malformed teacher reference cannot reach the
     * wire format and break the catalogue card.
     */
    public PublicTeacherSummaryResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
    }
}
