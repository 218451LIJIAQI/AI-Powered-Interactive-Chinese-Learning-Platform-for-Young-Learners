package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for the AI learning recommendation feature
 * ({@code POST /api/v1/ai/learning-recommendations}) — the
 * platform feeds a student's recent learning signals into the AI
 * and receives a personalised "what should I do next" suggestion.
 *
 * <p>The request is typically synthesised server-side from the
 * student's progress data
 * ({@link com.example.aichinese.dto.learning.StudentProgressSummaryResponse},
 * recent quiz attempts, recent lesson completions) rather than
 * filled in by the front-end — see
 * {@code AiLearningRecommendationService.recommendForCurrentStudent}.
 * The DTO is exposed in case bespoke front-ends want to override
 * specific signals.</p>
 *
 * <h2>Signal lists</h2>
 * <p>Each list is capped at 20 items / 200 chars per item to keep
 * the prompt token count bounded:</p>
 * <ul>
 *   <li>{@link #recentQuizResults} — short summaries like
 *       "HSK 1 Lesson 3 quiz: 75%".</li>
 *   <li>{@link #wrongAnswers} — texts of recently-missed quiz
 *       questions, used to identify pattern errors.</li>
 *   <li>{@link #weakAreas} — the same {@code feedbackType} tags
 *       carried through from teacher feedback (see
 *       {@code TeacherStudentSummaryResponse#weakAreas()}).</li>
 *   <li>{@link #completedLessons} — recent lesson titles, so the
 *       AI does not recommend something already mastered.</li>
 * </ul>
 *
 * <p>{@link #studentId} is optional and used for audit logging;
 * the recommendation is based on the supplied signals, not by
 * the AI looking up the student id directly.
 * {@link #languagePreference} controls the response language.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * the two short string fields ({@link #currentLevel} and
 * {@link #languagePreference}) so Bean Validation operates on
 * the canonical form and the service layer's sanitisation calls
 * are idempotent. The four list fields are intentionally
 * <em>not</em> normalised here — the
 * {@code AiService.joinPromptList} pipeline is the canonical
 * owner of trim, dedup, and per-prompt-fragment sanitisation for
 * those fields, mirroring the
 * {@code UpdateTeacherProfileRequest → TeacherManagementMapper.joinCategories}
 * convention. {@code null} is preserved on every field so
 * downstream code can distinguish "absent" from "explicitly
 * empty".</p>
 */
@JsonPropertyOrder({
        "studentId",
        "currentLevel",
        "recentQuizResults",
        "wrongAnswers",
        "weakAreas",
        "completedLessons",
        "languagePreference"
})
public record AiLearningRecommendationRequest(
        @Positive(message = "Student id must be positive.")
        Long studentId,
        @Size(max = 100, message = "Current level must not exceed 100 characters.")
        String currentLevel,
        @Size(max = 20, message = "Recent quiz results must not exceed 20 items.")
        List<@Size(max = 200, message = "Recent quiz result must not exceed 200 characters.") String> recentQuizResults,
        @Size(max = 20, message = "Wrong answers must not exceed 20 items.")
        List<@Size(max = 200, message = "Wrong answer must not exceed 200 characters.") String> wrongAnswers,
        @Size(max = 20, message = "Weak areas must not exceed 20 items.")
        List<@Size(max = 200, message = "Weak area must not exceed 200 characters.") String> weakAreas,
        @Size(max = 20, message = "Completed lessons must not exceed 20 items.")
        List<@Size(max = 200, message = "Completed lesson must not exceed 200 characters.") String> completedLessons,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    /**
     * Defensive canonical constructor: trims the two short text
     * fields so Bean Validation operates on the canonical form
     * and downstream sanitisation is idempotent. {@code null} is
     * preserved so downstream code can distinguish "absent" from
     * "explicitly empty". The four list fields are deliberately
     * left untouched — see class Javadoc.
     */
    public AiLearningRecommendationRequest {
        if (currentLevel != null) {
            currentLevel = currentLevel.trim();
        }
        if (languagePreference != null) {
            languagePreference = languagePreference.trim();
        }
    }
}
