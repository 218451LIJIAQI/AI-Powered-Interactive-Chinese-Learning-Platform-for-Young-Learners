package com.example.aichinese.dto.learning;

/**
 * Teacher-facing view of one quiz option, surfaced inside
 * {@link TeacherQuizQuestionResponse#options()}.
 *
 * <p>Mirrors {@link QuizOptionRequest} plus the database-assigned
 * {@link #id}. Crucially, surfaces {@link #correctOption} so the
 * authoring UI can render the green "correct" tick and let the
 * teacher verify the marked-correct option count is right (e.g. at
 * least one for {@code MULTIPLE_CHOICE}, exactly one for
 * {@code TRUE_FALSE}).</p>
 *
 * <p>The student-facing counterpart {@link QuizOptionResponse}
 * omits {@link #correctOption} — see the anti-cheat note there.</p>
 */
public record TeacherQuizOptionResponse(
        Long id,
        String optionText,
        boolean correctOption,
        Integer displayOrder) {
}
