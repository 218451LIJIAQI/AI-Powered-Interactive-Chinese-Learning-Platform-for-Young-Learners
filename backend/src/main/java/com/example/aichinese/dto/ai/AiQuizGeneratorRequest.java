package com.example.aichinese.dto.ai;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for the AI quiz generator
 * ({@code POST /api/v1/ai/quiz-generator}) — both teachers and
 * students can ask the AI to draft quiz questions on a given
 * topic.
 *
 * <h2>Dual usage</h2>
 * <p>{@link #targetRoleContext} ({@code "TEACHER"} or
 * {@code "STUDENT"}) tells the AI which audience the questions
 * are for so it can pick the right tone and difficulty:</p>
 * <ul>
 *   <li>Teacher: drafts questions to seed
 *       {@code CreateQuizQuestionRequest} — the teacher pastes
 *       the AI output into the quiz authoring form for review and
 *       publishing.</li>
 *   <li>Student: generates a quick self-quiz the student
 *       practises against without a persisted attempt.</li>
 * </ul>
 *
 * <h2>{@link #numberOfQuestions} cap</h2>
 * <p>Bounded {@code [1, 10]} both to control upstream AI cost
 * (each question costs tokens) and to keep the response
 * predictable. Larger generations should iterate.</p>
 *
 * <h2>{@link #questionTypes}</h2>
 * <p>Optional list of preferred question types from the
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}
 * vocabulary; capped at 10 items. When omitted the AI picks a
 * mix appropriate for the {@link #level}. Unsupported types are
 * ignored server-side.</p>
 *
 * <p>{@link #topic} is the only required field — the AI can
 * generate a basic quiz on any topic without further hints. See
 * {@link AiQuizGeneratorResponse} for the response shape with
 * embedded {@link AiGeneratedQuizQuestionResponse} rows.</p>
 */
public record AiQuizGeneratorRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 100, message = "Level must not exceed 100 characters.")
        String level,
        @Size(max = 100, message = "Category must not exceed 100 characters.")
        String category,
        @Min(value = 1, message = "Number of questions must be at least 1.")
        @Max(value = 10, message = "Number of questions must not exceed 10.")
        Integer numberOfQuestions,
        @Size(max = 10, message = "Question types must not exceed 10 items.")
        List<@Size(max = 40, message = "Question type must not exceed 40 characters.") String> questionTypes,
        @Size(max = 20, message = "Target role context must not exceed 20 characters.")
        String targetRoleContext,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {
}
