package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lifecycle state of a teacher {@code PayoutRequest}.
 *
 * <p>State machine:</p>
 *
 * <pre>
 *      PENDING ─┬─▶ APPROVED ─▶ PAID    (terminal)
 *               └─▶ REJECTED            (terminal)
 * </pre>
 *
 * <p>The teacher balance calculation in
 * {@code PayoutService.computeAvailableBalance} treats {@link #PENDING}
 * and {@link #APPROVED} as committed-but-not-yet-disbursed (so they are
 * excluded from "withdrawable") and additionally treats {@link #PAID} as
 * already-disbursed.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code PayoutRequest}. The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired Flyway
 * migration.</p>
 */
public enum PayoutStatus {

    /** Newly submitted by the teacher; awaiting admin approval. */
    PENDING,

    /** Admin approved the request; awaiting payment disbursement. */
    APPROVED,

    /** Admin declined the request; no funds will be transferred. */
    REJECTED,

    /** Funds have been transferred to the teacher's bank account. */
    PAID;

    /** States in which the request cannot transition any further. */
    public static final Set<PayoutStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(REJECTED, PAID));

    /**
     * States that count as "committed funds" against the teacher's
     * available balance (the amount has been earmarked even if not yet
     * actually disbursed).
     */
    public static final Set<PayoutStatus> COMMITTED =
            Collections.unmodifiableSet(EnumSet.of(PENDING, APPROVED, PAID));

    /**
     * States that have committed funds against the teacher's balance
     * but have NOT yet been disbursed -- equivalently,
     * {@code COMMITTED \ TERMINAL}. Surfaced on
     * {@code TeacherEarningSummaryResponse.pendingPayoutAmount}: the
     * total a teacher has requested-but-not-yet-received. Class-load
     * guard #5 below enforces this set-algebra identity so that adding
     * a new state to either {@link #COMMITTED} or {@link #TERMINAL}
     * without updating {@code IN_FLIGHT} fails fast.
     */
    public static final Set<PayoutStatus> IN_FLIGHT =
            Collections.unmodifiableSet(EnumSet.of(PENDING, APPROVED));

    /*
     * Startup-time financial-safety invariant guard.  These five checks
     * encode the relationship between the three category sets that
     * {@code PayoutService.computeAvailableBalance} and
     * {@code PayoutService.getTeacherEarningSummary} rely on:
     *
     *   1. PENDING is NOT in TERMINAL.  A pending request must always
     *      remain transitionable so the admin can approve or reject it.
     *
     *   2. REJECTED is NOT in COMMITTED.  Rejection releases the balance
     *      earmark; if a future contributor accidentally added REJECTED
     *      to COMMITTED, every rejected request would permanently
     *      reduce the teacher's withdrawable balance.
     *
     *   3. TERMINAL union COMMITTED == ALL.  Every state has a defined
     *      disposition relative to the balance.  An "orphan" state in
     *      neither set would silently produce false from both
     *      isTerminal() and isCommitted().
     *
     *   4. TERMINAL intersect COMMITTED == {PAID}.  PAID is the only
     *      state that is both an outcome AND counts against the balance
     *      -- once disbursed, the money has left the platform but is
     *      still part of the teacher's earnings ledger.  Any other
     *      state appearing in both sets would mean a single request is
     *      simultaneously "still earmarked" and "already done", a
     *      contradiction.
     *
     *   5. IN_FLIGHT == COMMITTED \ TERMINAL.  The set-algebra identity
     *      that defines IN_FLIGHT: committed against the balance but
     *      not yet disbursed.  Without this guard, adding a hypothetical
     *      DISPUTED state to COMMITTED while forgetting to add it to
     *      IN_FLIGHT would silently understate the teacher's pending
     *      payout total in the earnings summary.
     */
    static {
        if (TERMINAL.contains(PENDING)) {
            throw new AssertionError(
                    "PayoutStatus.PENDING must never be in TERMINAL -- a pending request "
                            + "must remain transitionable until an admin approves or rejects it.");
        }
        if (COMMITTED.contains(REJECTED)) {
            throw new AssertionError(
                    "PayoutStatus.REJECTED must never be in COMMITTED -- rejection "
                            + "releases the balance earmark.");
        }
        EnumSet<PayoutStatus> covered = EnumSet.copyOf(TERMINAL);
        covered.addAll(COMMITTED);
        if (!covered.equals(EnumSet.allOf(PayoutStatus.class))) {
            EnumSet<PayoutStatus> orphans = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "PayoutStatus has constants in neither TERMINAL nor COMMITTED; orphans: "
                            + orphans);
        }
        EnumSet<PayoutStatus> both = EnumSet.copyOf(TERMINAL);
        both.retainAll(COMMITTED);
        if (!both.equals(EnumSet.of(PAID))) {
            throw new AssertionError(
                    "PayoutStatus TERMINAL intersect COMMITTED must equal {PAID}; actual: " + both);
        }
        EnumSet<PayoutStatus> derivedInFlight = EnumSet.copyOf(COMMITTED);
        derivedInFlight.removeAll(TERMINAL);
        if (!derivedInFlight.equals(IN_FLIGHT)) {
            throw new AssertionError(
                    "PayoutStatus.IN_FLIGHT must equal COMMITTED \\ TERMINAL; expected: "
                            + derivedInFlight + ", actual: " + IN_FLIGHT);
        }
    }

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, PayoutStatus> LOOKUP =
            EnumLookups.indexByName(PayoutStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised payout status
     */
    public static Optional<PayoutStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised payout status
     */
    public static PayoutStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Payout status is required.",
                () -> "Invalid payout status. Allowed values are PENDING, APPROVED, REJECTED, "
                        + "or PAID.");
    }

    /** @return {@code true} when the request is still awaiting admin review. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** @return {@code true} when the request has reached a terminal state. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** @return {@code true} when the funds have been transferred. */
    public boolean isPaid() {
        return this == PAID;
    }

    /**
     * @return {@code true} when the amount counts as committed against the
     *         teacher's available balance (regardless of whether the
     *         disbursement has actually happened yet)
     */
    public boolean isCommitted() {
        return COMMITTED.contains(this);
    }
}
