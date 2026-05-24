package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Teacher-facing quiz detail returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/lessons/{lessonId}/quizzes}
 *       (paginated list)</li>
 *   <li>The response side of {@code POST}, {@code PUT},
 *       {@code POST .../questions}, {@code PUT
 *       /quiz-questions/{questionId}}, and {@code DELETE
 *       /quiz-questions/{questionId}} so the authoring UI always
 *       sees the freshly-saved state</li>
 * </ul>
 *
 * <p>Compared to {@link StudentQuizResponse}, this DTO additionally
 * surfaces:</p>
 * <ul>
 *   <li>{@link #contentStatus} — editorial lifecycle state (DRAFT
 *       quizzes are not visible to students).</li>
 *   <li>{@link #createdAt} / {@link #updatedAt} — audit timestamps
 *       used by the "Last edited" column on the authoring list.</li>
 *   <li>The embedded questions are
 *       {@link TeacherQuizQuestionResponse} rows, which expose the
 *       teacher's {@code correctAnswerText}, {@code explanation},
 *       and per-option {@code correctOption} flag — none of which
 *       reach the student-facing endpoints.</li>
 * </ul>
 *
 * <p>{@link #totalPoints} is the sum of question points; useful as
 * a quick sanity check while the teacher is balancing scoring
 * across questions.</p>
 */
public record TeacherQuizResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        ContentStatus contentStatus,
        Integer timeLimitMinutes,
        Integer totalPoints,
        List<TeacherQuizQuestionResponse> questions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
