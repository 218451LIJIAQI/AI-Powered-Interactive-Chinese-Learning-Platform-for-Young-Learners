package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #title}, {@link #contentStatus},
 *       {@link #createdAt} and {@link #updatedAt} are non-{@code null}
 *       per the entity schema's {@code NOT NULL} constraints.</li>
 *   <li>{@link #lessonId} may be {@code null} for stand-alone
 *       practice quizzes (and {@link #lessonTitle} is also
 *       {@code null} in that case).</li>
 *   <li>{@link #questions} is non-{@code null} (empty list for a
 *       freshly-created quiz shell) and defensively copied.</li>
 * </ul>
 *
 * @param id               {@code Quiz} identifier; never
 *                         {@code null}
 * @param lessonId         parent lesson id; {@code null} for
 *                         stand-alone practice quizzes
 * @param lessonTitle      denormalised lesson title; {@code null}
 *                         when {@link #lessonId} is {@code null}
 * @param title            quiz title; never {@code null}
 * @param description      optional long-form description
 * @param contentStatus    editorial lifecycle state; never
 *                         {@code null}
 * @param timeLimitMinutes countdown duration; {@code null} or
 *                         {@code 0} both mean "untimed"
 * @param totalPoints      denormalised sum of per-question points
 * @param questions        teacher-facing question rows (defensively
 *                         copied); never {@code null}
 * @param createdAt        row creation instant; never {@code null}
 * @param updatedAt        last-modification instant; never
 *                         {@code null}
 */
@JsonPropertyOrder({
        "id",
        "lessonId",
        "lessonTitle",
        "title",
        "description",
        "contentStatus",
        "timeLimitMinutes",
        "totalPoints",
        "questions",
        "createdAt",
        "updatedAt"
})
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

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / status / timestamp fields per the entity
     * schema's {@code NOT NULL} constraints, and defensively copies
     * {@link #questions} (with {@code null} → {@link List#of()}
     * normalisation) so external mutation cannot leak into the
     * bound payload after construction.
     */
    public TeacherQuizResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
