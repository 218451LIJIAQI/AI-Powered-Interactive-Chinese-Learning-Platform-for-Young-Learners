package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Delivery format of a {@code Lesson}.
 *
 * <p>Drives both UI presentation (show "Join now" vs "Watch later") and
 * scheduling logic (whether a {@code LiveClass} is required, whether a
 * recorded video must exist before the lesson can be published, etc.).</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on the
 * {@code Lesson} entity. The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired
 * Flyway migration.</p>
 */
public enum LearningType {

    /** Synchronous teacher-led session held at a scheduled time. */
    LIVE,

    /** Asynchronous lesson backed exclusively by pre-recorded video. */
    RECORDED,

    /** Combination of live sessions and recorded supplementary material. */
    HYBRID;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, LearningType> LOOKUP = EnumLookups.indexByName(LearningType.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised learning type
     */
    public static Optional<LearningType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised learning type
     */
    public static LearningType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Learning type is required.",
                () -> "Invalid learning type. Allowed values are LIVE, RECORDED, or HYBRID.");
    }

    /** @return {@code true} when at least one synchronous session is involved. */
    public boolean hasLiveComponent() {
        return this == LIVE || this == HYBRID;
    }

    /** @return {@code true} when at least one pre-recorded video is involved. */
    public boolean hasRecordedComponent() {
        return this == RECORDED || this == HYBRID;
    }
}
