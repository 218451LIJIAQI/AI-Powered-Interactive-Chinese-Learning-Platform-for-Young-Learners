package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Reference taxonomy row for one
 * {@link com.example.aichinese.entity.LearningLevel} (e.g.
 * "Beginner / HSK 1", "Intermediate / HSK 3-4"), returned by:
 * <ul>
 *   <li>{@code GET /api/v1/public/levels} — full catalogue for the
 *       public course-discovery UI.</li>
 *   <li>Embedded inside content responses
 *       ({@link LearningModuleResponse}, {@link LessonResponse},
 *       {@link CoursePackageResponse},
 *       {@link ContentSubmissionResponse},
 *       {@link PublicCourseSummaryResponse}, etc.) so the front-end
 *       can render the level chip without a follow-up lookup.</li>
 * </ul>
 *
 * <p>This is the read-only projection of
 * {@link com.example.aichinese.entity.LearningLevel}. Levels are
 * curated by admins and effectively static at the wire-format level
 * — the front-end caches them locally and refreshes on app
 * version change.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on {@link #id} and
 * {@link #name} — both map to NOT NULL schema columns
 * ({@code learning_levels.name} is also {@code UNIQUE}), so a row
 * missing either is structurally invalid and must not reach the
 * wire format. {@link #description} may legitimately be {@code null}
 * when the curator left the hover blurb unset.</p>
 *
 * @param id          taxonomy row id; never {@code null}
 * @param name        public-facing label (i18n-ready in a future
 *                    iteration; currently English-only); never
 *                    {@code null}
 * @param description optional explanatory blurb shown on hover;
 *                    nullable
 */
@JsonPropertyOrder({ "id", "name", "description" })
public record LearningLevelResponse(
        Long id,
        String name,
        String description) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * two fields the mapper guarantees from any persisted row so a
     * malformed level row cannot reach the wire format and break
     * catalogue chip rendering.
     */
    public LearningLevelResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
