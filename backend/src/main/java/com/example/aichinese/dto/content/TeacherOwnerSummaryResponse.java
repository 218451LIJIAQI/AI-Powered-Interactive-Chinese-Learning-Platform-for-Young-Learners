package com.example.aichinese.dto.content;

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
 */
public record TeacherOwnerSummaryResponse(
        Long teacherId,
        Long userId,
        String fullName,
        TeacherStatus teacherStatus) {
}
