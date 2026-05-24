package com.example.aichinese.dto.admin.activity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * {@code StudentActivityLibraryService}, which dispatches student-facing
 * queries by matching this enum directly.</p>
 *
 * <h2>Wire-format contract</h2>
 * <p>This is a wire-format enum: the constant names appear verbatim in
 * JSON payloads exchanged with the frontend (see the TypeScript
 * {@code AdminActivityType} union), so they MUST NOT be renamed without
 * a coordinated frontend change. Serialisation always emits the
 * canonical Java constant name (e.g. {@code "MATCHING_GAME"}).</p>
 *
 * <p>Deserialisation is split into two paths with different strictness:</p>
 * <ul>
 *   <li>{@link #fromJson(String)} — invoked by Jackson on JSON request
 *       bodies; tolerates kebab-case, lower-case and surrounding
 *       whitespace, and treats {@code null}/blank as "no value supplied"
 *       so that downstream {@code @NotNull} can surface a focused
 *       400.</li>
 *   <li>{@link #parseLenient(String)} — exposed for callers outside the
 *       Jackson pipeline (URL slugs, service-layer query parameters)
 *       that need the same tolerance but prefer an
 *       {@link Optional}-returning, never-throwing API.</li>
 * </ul>
 *
 * <p>{@link #slug()} is the inverse of {@link #parseLenient(String)} for
 * URL synthesis (e.g. {@code MATCHING_GAME → "matching-game"}), aligning
 * with the kebab-case path segments mounted by
 * {@code AdminActivityController}.</p>
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
     * O(1) case-insensitive lookup table built once at class-load time.
     * Keys are the canonical {@code UPPER_SNAKE_CASE} constant names,
     * upper-cased with {@link Locale#ROOT} to neutralise the Turkish-locale
     * {@code I → ı} hazard.
     */
    private static final Map<String, ActivityType> LOOKUP =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(
                            value -> value.name().toUpperCase(Locale.ROOT),
                            Function.identity()));

    /**
     * Eagerly-built, comma-separated list of canonical names appended to
     * the "unknown value" error message so the API caller learns which
     * values are accepted without a separate round trip. Computing this
     * once at class-load time keeps the hot deserialisation path
     * allocation-free.
     */
    private static final String ALLOWED_VALUES_LIST =
            Arrays.stream(values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

    /**
     * Lenient parser used by Jackson when this enum appears in inbound
     * JSON. Trims surrounding whitespace, normalises kebab-case to
     * snake_case and matches case-insensitively so {@code "matching-game"},
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
     *         but does not match any known constant; the global exception
     *         handler surfaces this as a 400 Bad Request and the message
     *         enumerates the valid values
     */
    @JsonCreator
    public static ActivityType fromJson(String value) {
        if (value == null) {
            return null;
        }
        String normalised = normalise(value);
        if (normalised == null) {
            return null;
        }
        ActivityType match = LOOKUP.get(normalised);
        if (match == null) {
            // Reject the original (untrimmed) value so the caller sees
            // exactly what they sent — but cap the echo to keep error
            // messages bounded for adversarial inputs.
            String echo = value.length() > 64 ? value.substring(0, 64) + "…" : value;
            throw new IllegalArgumentException(
                    "Unknown activity type: '" + echo
                            + "'. Allowed values are " + ALLOWED_VALUES_LIST + ".");
        }
        return match;
    }

    /**
     * Non-throwing variant of {@link #fromJson(String)} for callers
     * outside the Jackson pipeline (URL slugs, query-parameter parsers,
     * service-layer dispatch). Mirrors the {@code Optional.empty()}
     * convention used by {@code tryFrom} on the domain enums.
     *
     * @param value raw textual representation; may be {@code null}, blank,
     *              kebab-case, snake_case, mixed case, or surrounded by
     *              whitespace
     * @return matching constant, or {@link Optional#empty()} when the
     *         input is {@code null}, blank, or does not match any constant
     */
    public static Optional<ActivityType> parseLenient(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = normalise(value);
        if (normalised == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(normalised));
    }

    /**
     * Canonical URL-slug form of this constant, i.e. lower-cased
     * kebab-case ({@code MATCHING_GAME → "matching-game"}). Used where a
     * caller needs to synthesise a URL path matching the kebab-case
     * segments mounted by {@code AdminActivityController}.
     *
     * @return lower-cased kebab-case slug, never {@code null} or blank
     */
    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Trim, kebab-to-snake-case, upper-case shared by both lenient
     * lookup paths. Returns {@code null} for blank input so callers can
     * collapse "missing" and "blank" into a single early-return branch.
     */
    private static String normalise(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
