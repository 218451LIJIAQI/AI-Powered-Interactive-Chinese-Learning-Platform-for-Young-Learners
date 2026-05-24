package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Compact teacher reference embedded inside the {@code teacher}
 * field of every owned-content response
 * ({@link LearningModuleResponse}, {@link LessonResponse},
 * {@link CoursePackageResponse},
 * {@link ContentSubmissionResponse}, etc.).
 *
 * <p>Carries the minimum identity an admin / fellow teacher needs
 * to attribute the content. Surfaces both id columns because the
 * navigation distinguishes {@link #teacherId}
 * (the {@code teacher_profiles.id} for "Teacher detail" screens)
 * from {@link #userId} (the {@code users.id} for messaging /
 * admin moderation tools).</p>
 *
 * <h2>{@link #fullName}</h2>
 * <p>The record field is named "full name" for historical reasons,
 * but the value comes from {@code users.display_name} (the
 * {@code users} table has no {@code full_name} column — the same
 * naming carries through from
 * {@code TeacherStudentSummaryResponse#fullName}).</p>
 *
 * <h2>{@link #teacherStatus}</h2>
 * <p>The owner's verification state. Only
 * {@link TeacherStatus#APPROVED} teachers can publish content to
 * the public catalogue
 * (see {@code PublicContentService.discoverCourses}), but the
 * admin-facing screens surface {@link TeacherStatus#PENDING} and
 * {@link TeacherStatus#REJECTED} owners as well so admins can
 * triage authorship issues.</p>
 *
 * <p>The student-facing {@link PublicTeacherSummaryResponse} is the
 * counterpart with strictly less information (no
 * {@link #userId}, no full status — only a single
 * {@code verified} boolean).</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the three
 * fields the mapper guarantees from any persisted teacher row
 * ({@link #teacherId}, {@link #userId}, {@link #teacherStatus}) —
 * the mapper only emits this DTO once it has verified both the
 * teacher profile and its user association hydrated (see
 * {@link com.example.aichinese.mapper.ContentManagementMapper#toTeacherSummary}).
 * {@link #fullName} may legitimately be {@code null} when the
 * teacher has not yet set a display name on their user row.</p>
 *
 * @param teacherId     {@code teacher_profiles.id}; never
 *                      {@code null}
 * @param userId        {@code users.id} of the teacher's user
 *                      row; never {@code null}
 * @param fullName      denormalised {@code users.display_name};
 *                      nullable when unset
 * @param teacherStatus owner's verification state; never
 *                      {@code null}
 */
@JsonPropertyOrder({ "teacherId", "userId", "fullName", "teacherStatus" })
public record TeacherOwnerSummaryResponse(
        Long teacherId,
        Long userId,
        String fullName,
        TeacherStatus teacherStatus) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the mapper guarantees so a malformed teacher
     * reference cannot reach the wire format and break attribution
     * on the admin / teacher dashboards.
     */
    public TeacherOwnerSummaryResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
    }
}
