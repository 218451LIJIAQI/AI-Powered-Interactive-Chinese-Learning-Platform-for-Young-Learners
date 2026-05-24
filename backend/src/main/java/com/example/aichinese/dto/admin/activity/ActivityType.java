package com.example.aichinese.dto.admin.activity;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Discriminator that identifies which of the six platform-managed
 * learning activity tables a row belongs to.
 *
 * <p>Used by cross-type admin endpoints (the unified library browser
 * and the bulk status-update path) so a single payload can refer to
 * any activity without one DTO per type. The same discriminator is
 * carried back to the client inside
 * {@link AdminActivitySummaryResponse#type()} so the front-end knows
 * which detail endpoint to call when the admin clicks a row.</p>
 *
 * <p>Each constant maps 1:1 to one entity type and one DB table:</p>
 * <ul>
 *   <li>{@link #FLASHCARD} — {@code flashcards} /
 *       {@code com.example.aichinese.entity.Flashcard}</li>
 *   <li>{@link #MATCHING_GAME} — {@code matching_games} /
 *       {@code com.example.aichinese.entity.MatchingGame}</li>
 *   <li>{@link #LISTENING_EXERCISE} — {@code listening_exercises} /
 *       {@code com.example.aichinese.entity.ListeningExercise}</li>
 *   <li>{@link #TRACING_EXERCISE} — {@code tracing_exercises} /
 *       {@code com.example.aichinese.entity.TracingExercise}</li>
 *   <li>{@link #DRAG_DROP_ACTIVITY} — {@code drag_drop_activities} /
 *       {@code com.example.aichinese.entity.DragDropActivity}</li>
 *   <li>{@link #STORY_ACTIVITY} — {@code story_activities} /
 *       {@code com.example.aichinese.entity.StoryActivity}</li>
 * </ul>
 *
 * <p>The same six artefact types are surfaced to learners through
 * {@code StudentActivityLibraryService}, which uses this enum (or the
 * lower-cased URL form of it) to dispatch student-facing queries.</p>
 *
 * <h2>Wire-format contract</h2>
 * <p>This is a wire-format enum: the constant names appear in JSON
 * payloads exchanged with the frontend, so they MUST NOT be renamed
 * without a coordinated frontend change. Serialisation always uses the
 * canonical Java constant name (e.g. {@code "MATCHING_GAME"}).
 * Deserialisation is leniently performed via {@link #fromJson(String)},
 * which accepts both the canonical form and common variants
 * (lower-case, kebab-case, leading/trailing whitespace) so the same
 * enum can safely back both JSON request bodies and URL slugs.</p>
 */
public enum ActivityType {

    /** Vocabulary flashcard — see {@code Flashcard}. */
    FLASHCARD,

    /** Pair-matching game — see {@code MatchingGame}. */
    MATCHING_GAME,

    /** Audio comprehension exercise — see {@code ListeningExercise}. */
    LISTENING_EXERCISE,

    /** Character handwriting practice — see {@code TracingExercise}. */
    TRACING_EXERCISE,

    /** Drag-and-drop categorisation — see {@code DragDropActivity}. */
    DRAG_DROP_ACTIVITY,

    /** Short-story reading + comprehension — see {@code StoryActivity}. */
    STORY_ACTIVITY;

    /**
     * Lenient parser used by Jackson when this enum appears in inbound
     * JSON. Trims surrounding whitespace, normalises kebab-case to
     * snake_case, and matches case-insensitively so {@code "matching-game"},
     * {@code "Matching_Game"} and {@code "MATCHING_GAME"} all resolve to
     * {@link #MATCHING_GAME}. {@code null} and blank input return
     * {@code null} (treat as "no value supplied") rather than throwing,
     * which lets caller-side {@code @NotNull} produce a single,
     * field-aware validation error instead of a 500.
     *
     * @param value raw JSON token; may be {@code null}, blank, or
     *              non-canonical casing
     * @return matched constant, or {@code null} when {@code value} is
     *         {@code null} / blank
     * @throws IllegalArgumentException when {@code value} is non-blank
     *         but does not match any known constant; Jackson surfaces
     *         this as a 400 Bad Request via the global exception handler
     */
    @JsonCreator
    public static ActivityType fromJson(String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim();
        if (normalised.isEmpty()) {
            return null;
        }
        normalised = normalised.replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return ActivityType.valueOf(normalised);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown activity type: " + value, ex);
        }
    }
}
