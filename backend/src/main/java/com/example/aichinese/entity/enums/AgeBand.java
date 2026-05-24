package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Age bracket targeted by a platform-managed learning activity.
 *
 * <p>Admins pick exactly one value per activity so the student-facing
 * library can surface age-appropriate content. {@link #ALL_AGES} acts as
 * a wildcard that always matches the learner's age, regardless of which
 * other band the student profile reports.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on activity
 * entities (drag-drop, flashcard, listening, matching, story, tracing).
 * The constant names are part of the database contract — they MUST NOT
 * be renamed or removed without a paired Flyway migration.</p>
 */
public enum AgeBand {

    /** Pre-school children, typically ages 4-6. */
    KINDERGARTEN,

    /** Primary school years 1-3, typically ages 7-9. */
    LOWER_PRIMARY,

    /** Primary school years 4-6, typically ages 10-12. */
    UPPER_PRIMARY,

    /** Wildcard band that is appropriate for every learner. */
    ALL_AGES;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AgeBand> LOOKUP = EnumLookups.indexByName(AgeBand.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised age band
     */
    public static Optional<AgeBand> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised age band
     */
    public static AgeBand fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Age band is required.",
                () -> "Invalid age band. Allowed values are KINDERGARTEN, LOWER_PRIMARY, "
                        + "UPPER_PRIMARY, or ALL_AGES.");
    }

    /** @return {@code true} when this is the {@link #ALL_AGES} wildcard. */
    public boolean isAllAges() {
        return this == ALL_AGES;
    }

    /**
     * Library/filter helper: an activity tagged with this band is shown to
     * learners whose own band is {@code learnerBand} when either band is
     * {@link #ALL_AGES} or when both bands match exactly.
     *
     * @param learnerBand the band the student profile reports; {@code null}
     *                    is treated as a no-restriction wildcard
     * @return {@code true} when this activity is appropriate for the learner
     */
    public boolean matches(AgeBand learnerBand) {
        return learnerBand == null
                || this == ALL_AGES
                || learnerBand == ALL_AGES
                || this == learnerBand;
    }
}
