package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lifecycle state of a student's reservation for a {@code LiveClass}.
 *
 * <p>State machine:</p>
 *
 * <pre>
 *      ENROLLED ─┬─▶ ATTENDED   (terminal)
 *                ├─▶ MISSED     (terminal)
 *                └─▶ CANCELLED  (terminal)
 * </pre>
 *
 * <p>{@link #ATTENDED} and {@link #MISSED} are typically set automatically
 * by a scheduled job after the session ends; {@link #CANCELLED} is set
 * by the student before the session begins.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code LiveClassEnrolment}. The constant names are part of the
 * database contract — they MUST NOT be renamed or removed without a
 * paired Flyway migration.</p>
 */
public enum LiveClassEnrolmentStatus {

    /** Student has reserved a seat and may attend or cancel. */
    ENROLLED,

    /** Student withdrew their reservation before the session started. */
    CANCELLED,

    /** Student successfully attended the session. */
    ATTENDED,

    /** Student did not attend and did not cancel in time. */
    MISSED;

    /** States in which the reservation can no longer transition. */
    public static final Set<LiveClassEnrolmentStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(CANCELLED, ATTENDED, MISSED));

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, LiveClassEnrolmentStatus> LOOKUP =
            EnumLookups.indexByName(LiveClassEnrolmentStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised enrolment status
     */
    public static Optional<LiveClassEnrolmentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised enrolment status
     */
    public static LiveClassEnrolmentStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Live class enrolment status is required.",
                () -> "Invalid live class enrolment status. Allowed values are ENROLLED, "
                        + "CANCELLED, ATTENDED, or MISSED.");
    }

    /** @return {@code true} when the student is still expected to attend. */
    public boolean isActive() {
        return this == ENROLLED;
    }

    /** @return {@code true} when the reservation has reached a terminal state. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** @return {@code true} when the student successfully attended the session. */
    public boolean isAttended() {
        return this == ATTENDED;
    }

    /** @return {@code true} when the student withdrew their reservation. */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
