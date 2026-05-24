package com.example.aichinese.dto.learning;

/**
 * Student-facing view of a quiz option, surfaced inside
 * {@link StudentQuizQuestionResponse#options()}.
 *
 * <p>Deliberately <em>omits</em> the {@code correctOption} flag that
 * {@link QuizOptionRequest} and {@link TeacherQuizOptionResponse}
 * carry — graded quizzes are part of the assessment engine and must
 * not leak the answer before the student submits. This is the
 * pragmatic anti-cheat boundary for quizzes (contrast with the
 * platform-managed activity library, where the answer <em>is</em> on
 * the wire because XP is not gated on correctness — see the note on
 * {@code StudentStoryQuestionResponse#optionsPayload()}).</p>
 *
 * <p>{@link #displayOrder} preserves the teacher's canonical order;
 * the front-end may shuffle on each play to prevent positional
 * memorisation.</p>
 */
public record QuizOptionResponse(
        Long id,
        String optionText,
        Integer displayOrder) {
}
