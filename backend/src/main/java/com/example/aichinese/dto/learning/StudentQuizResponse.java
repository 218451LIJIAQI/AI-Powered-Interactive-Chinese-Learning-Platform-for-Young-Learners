package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id} and {@link #title} are non-{@code null} — the
 *       front-end deep-links by id and renders the title
 *       unconditionally.</li>
 *   <li>{@link #lessonId} may be {@code null} for stand-alone
 *       practice quizzes that aren't bound to any lesson; in that
 *       case {@link #lessonTitle} is also {@code null}.</li>
 *   <li>{@link #questions} is non-{@code null} (empty list when the
 *       quiz has not yet been populated) and defensively copied.</li>
 * </ul>
 *
 * @param id               {@link com.example.aichinese.entity.Quiz}
 *                         identifier; never {@code null}
 * @param lessonId         parent lesson id; {@code null} for
 *                         stand-alone practice quizzes
 * @param lessonTitle      denormalised lesson title; {@code null}
 *                         when {@link #lessonId} is {@code null}
 * @param title            quiz title; never {@code null}
 * @param description      optional long-form description
 * @param timeLimitMinutes countdown duration in minutes;
 *                         {@code null} or {@code 0} both mean
 *                         "untimed"
 * @param totalPoints      denormalised sum of per-question
 *                         {@code points}; may be {@code null} on
 *                         legacy rows
 * @param questions        pre-submission question views (defensively
 *                         copied); never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "lessonId",
        "lessonTitle",
        "title",
        "description",
        "timeLimitMinutes",
        "totalPoints",
        "questions"
})
public record StudentQuizResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        Integer timeLimitMinutes,
        Integer totalPoints,
        List<StudentQuizQuestionResponse> questions) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the quiz player renders
     * unconditionally, and defensively copies {@link #questions}
     * (with {@code null} → {@link List#of()} normalisation) so
     * external mutation cannot leak into the bound payload after
     * construction.
     */
    public StudentQuizResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
