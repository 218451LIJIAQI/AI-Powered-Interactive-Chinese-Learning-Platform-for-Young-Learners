package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

/**
 * Student-facing view of one teacher feedback row, returned by
 * {@code GET /api/v1/students/teacher-feedback} (paginated; the
 * student's own received feedback, newest first).
 *
 * <p>Drives the student's "Feedback from teachers" inbox.
 * Compared to the teacher-facing {@link TeacherFeedbackResponse}:</p>
 * <ul>
 *   <li>Drops {@code teacherId}/{@code studentId}/{@code studentName}
 *       — the student does not need to navigate to the teacher's
 *       admin profile, and the student is the caller so their own
 *       identity is implicit.</li>
 *   <li>Keeps {@link #teacherName} (denormalised from
 *       {@code users.display_name}) so the inbox renders as
 *       "Feedback from Dr. Chen" without a follow-up lookup.</li>
 *   <li>Keeps both lesson and quiz-attempt linkage so the student
 *       can deep-link from the feedback card back to the
 *       referenced lesson or attempt review.</li>
 * </ul>
 *
 * <p>Students cannot edit or delete teacher feedback — the inbox
 * is read-only from the student's perspective. See
 * {@link CreateTeacherFeedbackRequest} for the authoring side.</p>
 */
public record StudentTeacherFeedbackResponse(
        Long id,
        String teacherName,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle,
        String feedbackType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
