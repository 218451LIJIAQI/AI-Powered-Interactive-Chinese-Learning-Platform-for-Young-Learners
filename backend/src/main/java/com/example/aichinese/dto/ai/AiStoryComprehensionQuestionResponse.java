package com.example.aichinese.dto.ai;

import java.util.List;

/**
 * One AI-generated comprehension question inside
 * {@link AiStoryGeneratorResponse#comprehensionQuestions()}.
 *
 * <p>Always multiple-choice for the story-comprehension surface
 * (simpler shape than the more general
 * {@link AiGeneratedQuizQuestionResponse}). {@link #options} is
 * the answer pool; {@link #answer} is the literal correct option
 * string (the front-end matches it back to {@link #options} to
 * flag the right answer once the student submits).</p>
 *
 * <p>{@link #explanation} is shown after the student picks an
 * answer, regardless of correctness — the AI explains in the
 * student's preferred language why the right answer is right,
 * grounded in the surrounding story body. This is the same
 * "explain after submit" pattern documented on
 * {@code QuizAttemptResultQuestionResponse}.</p>
 *
 * <p>Story comprehension does not award XP — it is a pure
 * self-assessment surface for reading practice. Anti-cheat
 * concerns therefore do not apply (cf. the
 * {@code QuizOptionResponse} anti-cheat note); the answer is on
 * the wire because the student already trusted themselves to
 * read the story honestly.</p>
 */
public record AiStoryComprehensionQuestionResponse(
        String question,
        List<String> options,
        String answer,
        String explanation) {
}
