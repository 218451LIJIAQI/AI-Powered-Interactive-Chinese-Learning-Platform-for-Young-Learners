package com.example.aichinese.dto.learning;

import java.util.List;

/**
 * Full quiz detail returned by
 * {@code GET /api/v1/students/quizzes/{quizId}} — the payload the
 * student receives when they open a quiz to begin an attempt.
 *
 * <p>Carries the same headline metadata as
 * {@link StudentQuizSummaryResponse} plus the embedded
 * {@link #questions} list. Each question is a
 * {@link StudentQuizQuestionResponse}, whose options
 * ({@link QuizOptionResponse}) deliberately do <em>not</em> carry
 * the {@code correctOption} flag — see the anti-cheat note on
 * {@link QuizOptionResponse}.</p>
 *
 * <p>For the teacher's view of the same quiz (with correct flags
 * and the editorial {@code contentStatus}), see
 * {@link TeacherQuizResponse}.</p>
 */
public record StudentQuizResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        Integer timeLimitMinutes,
        Integer totalPoints,
        List<StudentQuizQuestionResponse> questions) {
}
