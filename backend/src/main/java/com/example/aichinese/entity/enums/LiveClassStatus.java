package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lifecycle state of a {@code LiveClass} session.
 *
 * <p>State machine (simplified):</p>
 *
 * <pre>
 *      SCHEDULED ─┬─▶ ONGOING ──▶ COMPLETED   (terminal)
 *                 ├─▶ CANCELLED               (terminal)
 *                 └─▶ RESCHEDULED ─▶ SCHEDULED (loops back)
 * </pre>
 *
 * <p>The frontend join gate (see {@code LiveLearningAccessService}) only
 * permits {@link #SCHEDULED} and {@link #ONGOING} sessions to be
 * launched; the public listing in {@code LiveClassService} additionally
 * exposes {@link #RESCHEDULED} so students can see the new time before
 * the session is republished as {@code SCHEDULED}.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code LiveClass}. The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired Flyway
 * migration.</p>
 */
public enum LiveClassStatus {

    /** Session is on the calendar and has not yet started. */
    SCHEDULED,

    /** Session is currently in progress. */
    ONGOING,

    /** Session has finished as planned. */
    COMPLETED,

    /** Session was cancelled before completion; no recording is expected. */
    CANCELLED,

    /** Session was moved to a new time; a new {@link #SCHEDULED} entry follows. */
    RESCHEDULED;

    /**
     * Statuses for which the meeting URL may be opened. Mirrors the
     * existing {@code LiveLearningAccessService.JOINABLE_STATUSES}
     * constant — a single source of truth here keeps both in sync.
     */
    public static final Set<LiveClassStatus> JOINABLE = Set.of(SCHEDULED, ONGOING);

    /**
     * Statuses surfaced in the student-visible listing. Mirrors
     * {@code LiveClassService.STUDENT_VISIBLE_STATUSES}.
     */
    public static final Set<LiveClassStatus> STUDENT_VISIBLE =
            Set.of(SCHEDULED, ONGOING, RESCHEDULED);

    /** Statuses in which the session can no longer transition. */
    public static final Set<LiveClassStatus> TERMINAL = Set.of(COMPLETED, CANCELLED);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, LiveClassStatus> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised status
     */
    public static Optional<LiveClassStatus> tryFrom(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(trimmed.toUpperCase(Locale.ROOT)));
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised status
     */
    public static LiveClassStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Live class status is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid live class status. Allowed values are SCHEDULED, ONGOING, "
                        + "COMPLETED, CANCELLED, or RESCHEDULED."));
    }

    /** @return {@code true} when the session is currently in progress. */
    public boolean isOngoing() {
        return this == ONGOING;
    }

    /** @return {@code true} when students may launch the meeting URL right now. */
    public boolean isJoinable() {
        return JOINABLE.contains(this);
    }

    /** @return {@code true} when the session should appear in the student listing. */
    public boolean isStudentVisible() {
        return STUDENT_VISIBLE.contains(this);
    }

    /** @return {@code true} when the session is in a terminal state. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
