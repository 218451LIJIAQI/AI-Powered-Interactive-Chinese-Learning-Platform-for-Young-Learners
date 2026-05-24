package com.example.aichinese.dto.ai;

import java.util.List;

/**
 * One AI-generated question row inside
 * {@link AiQuizGeneratorResponse#questions()}.
 *
 * <p>Mirrors the shape of the manual-authoring
 * {@code CreateQuizQuestionRequest} closely so a teacher
 * accepting the AI output can paste the row into the authoring
 * form with minimal mapping. {@link #questionType} carries a
 * string from the
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}
 * vocabulary; {@link #options} is populated for option-based
 * types and empty for free-text / structured-payload types.</p>
 *
 * <p>{@link #correctAnswer} is plain text — for option-based
 * questions, it is the literal correct option string (the
 * front-end matches it back to {@link #options} to flag the
 * correct row when materialising into
 * {@code QuizOptionRequest} children).</p>
 *
 * <h2>Scaffolding extras</h2>
 * <p>{@link #pinyin} and {@link #translation} are AI-supplied
 * Mandarin-learner aids — not present on the manual-authoring
 * DTO. They let the front-end show "What does this question say
 * in English?" before the teacher commits the question, helping
 * the teacher spot AI translation errors.</p>
 *
 * <p>{@link #explanation} carries the post-submission rationale
 * (same as {@code QuizAttemptResultQuestionResponse#explanation}).</p>
 */
public record AiGeneratedQuizQuestionResponse(
        String questionText,
        String questionType,
        List<String> options,
        String correctAnswer,
        String explanation,
        String pinyin,
        String translation) {
}
