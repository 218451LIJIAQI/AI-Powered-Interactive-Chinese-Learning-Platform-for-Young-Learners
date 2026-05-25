package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lifecycle state of a {@code RefundRequest}.
 *
 * <p>State machine:</p>
 *
 * <pre>
 *      PENDING ─┬─▶ APPROVED ─▶ REFUNDED  (terminal)
 *               └─▶ REJECTED              (terminal)
 * </pre>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code RefundRequest}. The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired Flyway
 * migration.</p>
 */
public enum RefundStatus {

    /** Newly submitted by the student; awaiting admin review. */
    PENDING,

    /** Admin approved the request; awaiting provider settlement. */
    APPROVED,

    /** Admin declined the request; no funds will be returned. */
    REJECTED,

    /** Provider has settled the refund and funds were returned to the buyer. */
    REFUNDED;

    /** States in which the request cannot transition any further. */
    public static final Set<RefundStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(REJECTED, REFUNDED));

    /** States in which the request still requires action before becoming terminal. */
    public static final Set<RefundStatus> ACTIONABLE =
            Collections.unmodifiableSet(EnumSet.of(PENDING, APPROVED));

    /*
     * Startup-time perfect-complement guard.
     *
     *   1. TERMINAL union ACTIONABLE == ALL.  Every state must be
     *      either terminal or still actionable.  An "orphan" state in
     *      neither set would silently disappear from the admin's
     *      "needs decision" queue (which filters by ACTIONABLE) and
     *      from the terminal-records archive.
     *
     *   2. TERMINAL intersect ACTIONABLE == empty.  No state may be
     *      both terminal AND actionable, otherwise the admin could
     *      double-act on an already-decided request: approve a
     *      REJECTED refund, or reject a REFUNDED one, both of which
     *      would emit duplicate notifications and corrupt the
     *      financial audit trail.
     *
     * Both checks run exactly once at class load with EnumSet
     * bit-operations and fail fast with a message naming the
     * offending constants.
     */
    static {
        EnumSet<RefundStatus> covered = EnumSet.copyOf(TERMINAL);
        covered.addAll(ACTIONABLE);
        if (!covered.equals(EnumSet.allOf(RefundStatus.class))) {
            EnumSet<RefundStatus> orphans = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "RefundStatus has constants in neither TERMINAL nor ACTIONABLE; orphans: "
                            + orphans);
        }
        EnumSet<RefundStatus> overlap = EnumSet.copyOf(TERMINAL);
        overlap.retainAll(ACTIONABLE);
        if (!overlap.isEmpty()) {
            throw new AssertionError(
                    "RefundStatus TERMINAL and ACTIONABLE must be disjoint; overlapping: "
                            + overlap);
        }
    }

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, RefundStatus> LOOKUP =
            EnumLookups.indexByName(RefundStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised refund status
     */
    public static Optional<RefundStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised refund status
     */
    public static RefundStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Refund status is required.",
                () -> "Invalid refund status. Allowed values are PENDING, APPROVED, REJECTED, "
                        + "or REFUNDED.");
    }

    /** @return {@code true} when the refund request is still awaiting admin review. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** @return {@code true} when the request has reached a terminal state. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** @return {@code true} when the request can still be acted upon. */
    public boolean isActionable() {
        return ACTIONABLE.contains(this);
    }

    /** @return {@code true} when the buyer has been reimbursed. */
    public boolean isRefunded() {
        return this == REFUNDED;
    }
}
