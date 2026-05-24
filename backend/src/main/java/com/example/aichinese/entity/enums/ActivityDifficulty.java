package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Difficulty rating assigned by an admin to a platform-managed learning
 * activity (drag-drop, flashcard, listening, matching, story, tracing).
 *
 * <p>Used by:</p>
 * <ul>
 *   <li>The student library to filter age-appropriate exercises.</li>
 *   <li>The XP/reward layer to scale points awarded on completion via
 *       {@link #xpMultiplier()}.</li>
 *   <li>The teacher analytics view to surface skill distribution.</li>
 * </ul>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)}, so the constant
 * names are part of the database contract — they MUST NOT be renamed or
 * removed without a paired Flyway migration.</p>
 */
public enum ActivityDifficulty {

    /** Introductory level suitable for absolute beginners. */
    EASY,

    /** Standard level expected for the majority of learners at the target age. */
    MEDIUM,

    /** Advanced level intended for fluency reinforcement and challenge. */
    HARD;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, ActivityDifficulty> LOOKUP =
            EnumLookups.indexByName(ActivityDifficulty.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised difficulty
     */
    public static Optional<ActivityDifficulty> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised difficulty
     */
    public static ActivityDifficulty fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Activity difficulty is required.",
                () -> "Invalid activity difficulty. Allowed values are EASY, MEDIUM, or HARD.");
    }

    /**
     * Stable XP multiplier keyed off the difficulty tier. Returned as a
     * {@code double} rather than a hard-coded constant so callers can
     * compose richer reward formulas (e.g. multiply by streak bonuses)
     * without a {@code switch} per call site.
     *
     * @return {@code 1.0} for {@link #EASY}, {@code 1.5} for {@link #MEDIUM},
     *         {@code 2.0} for {@link #HARD}
     */
    public double xpMultiplier() {
        return switch (this) {
            case EASY -> 1.0d;
            case MEDIUM -> 1.5d;
            case HARD -> 2.0d;
        };
    }
}
