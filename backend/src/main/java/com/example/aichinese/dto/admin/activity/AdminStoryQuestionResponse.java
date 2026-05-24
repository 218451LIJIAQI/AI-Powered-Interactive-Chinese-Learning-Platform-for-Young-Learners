package com.example.aichinese.dto.admin.activity;

/**
 * Read-only mirror of {@link AdminStoryQuestionRequest} returned
 * inside the parent {@link AdminStoryActivityResponse#questions()} list.
 *
 * <p>Adds the database-assigned {@link #id} so the front-end can
 * distinguish "edit existing question" from "insert new question"
 * when the admin re-saves the story. Questions are emitted in
 * {@code ORDER BY sort_order ASC, created_at ASC} (see
 * {@code StoryQuestionRepository}). {@link #optionsPayload} is emitted
 * verbatim — the admin UI re-parses it client-side to render the
 * option editor.</p>
 *
 * <p>This record carries no validation annotations: it is server-built
 * from already-persisted data, never bound from a request body, and
 * being a {@code record} it is structurally immutable.</p>
 *
 * @param id             database-assigned primary key
 * @param questionText   prompt text shown to the learner
 * @param optionsPayload opaque JSON encoding the option list and
 *                       correct answer; the front-end is the canonical
 *                       schema owner
 * @param explanation    optional rationale shown after submission
 * @param sortOrder      canonical authoring order
 */
public record AdminStoryQuestionResponse(
        Long id,
        String questionText,
        String optionsPayload,
        String explanation,
        Integer sortOrder) {
}
