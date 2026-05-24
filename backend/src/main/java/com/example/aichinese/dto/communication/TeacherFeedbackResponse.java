package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

/**
 * Teacher-facing feedback row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/feedback} (paginated;
 *       teacher's own feedback history)</li>
 *   <li>The response side of {@code POST}, {@code PUT}, and
 *       {@code DELETE} on {@code /api/v1/teachers/feedback}</li>
 * </ul>
 *
 * <p>Surfaces both the teacher and student identity
 * (denormalised {@code displayName} from {@code users}) plus the
 * optional lesson / quiz target (with denormalised titles) so the
 * teacher's "Feedback I've given" page renders without follow-up
 * lookups.</p>
 *
 * <p>The student-facing counterpart
 * {@link StudentTeacherFeedbackResponse} omits the student
 * identity (the student is the caller) and slims the teacher
 * identity to the display name only.</p>
 *
 * <p>{@link #lessonId}/{@link #lessonTitle} are populated when the
 * feedback targets a lesson; {@link #quizAttemptId}/{@link #quizTitle}
 * when it targets a quiz attempt; both pairs may be {@code null}
 * for general "encouragement" notes attached to a student
 * directly.</p>
 */
public record TeacherFeedbackResponse(
        Long id,
        Long teacherId,
        String teacherName,
        Long studentId,
        String studentName,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle,
        String feedbackType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
