package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Lightweight quiz row in the student-facing list endpoint
 * {@code GET /api/v1/students/quizzes} (paginated, optional
 * {@code lessonId} filter).
 *
 * <p>Drives the "Available quizzes" tab in the student UI; the
 * detail view ({@link StudentQuizResponse}) is fetched lazily when
 * the student opens a specific quiz. Surfacing only
 * {@link #questionCount} (instead of the full question array) keeps
 * the list payload small even for quizzes with dozens of
 * questions.</p>
 *
 * <p>Only quizzes whose parent lesson is published and accessible to
 * the student reach this endpoint. {@link #timeLimitMinutes} of
 * {@code 0} or {@code null} both mean "untimed" — see
 * {@link CreateQuizRequest}.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id} and {@link #title} are non-{@code null}.</li>
 *   <li>{@link #lessonId} may be {@code null} for stand-alone
 *       practice quizzes; in that case {@link #lessonTitle} is
 *       also {@code null}.</li>
 *   <li>{@link #questionCount} is non-negative.</li>
 * </ul>
 *
 * @param id               {@link com.example.aichinese.entity.Quiz}
 *                         identifier; never {@code null}
 * @param lessonId         parent lesson id; needed for breadcrumb
 *                         navigation; {@code null} for stand-alone
 *                         practice quizzes
 * @param lessonTitle      denormalised lesson title to avoid a
 *                         follow-up GET; {@code null} when
 *                         {@link #lessonId} is {@code null}
 * @param title            quiz title; never {@code null}
 * @param description      optional quiz description; may be
 *                         {@code null}
 * @param timeLimitMinutes duration cap; {@code 0} or {@code null}
 *                         means untimed
 * @param totalPoints      sum of {@code points} across all
 *                         questions; may be {@code null} on legacy
 *                         rows
 * @param questionCount    number of questions on the quiz;
 *                         non-negative
 */
@JsonPropertyOrder({
        "id",
        "lessonId",
        "lessonTitle",
        "title",
        "description",
        "timeLimitMinutes",
        "totalPoints",
        "questionCount"
})
public record StudentQuizSummaryResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        Integer timeLimitMinutes,
        Integer totalPoints,
        int questionCount) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the "Available quizzes" tab renders
     * unconditionally, and enforces the non-negative invariant on
     * {@link #questionCount}.
     */
    public StudentQuizSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        if (questionCount < 0) {
            throw new IllegalArgumentException("questionCount must be non-negative, was " + questionCount);
        }
    }
}
