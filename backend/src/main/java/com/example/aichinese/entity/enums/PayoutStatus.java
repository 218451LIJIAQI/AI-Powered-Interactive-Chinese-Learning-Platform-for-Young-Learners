package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public static final Set<PayoutStatus> TERMINAL = Set.of(REJECTED, PAID);

    /**
     * States that count as "committed funds" against the teacher's
     * available balance (the amount has been earmarked even if not yet
     * actually disbursed).
     */
    public static final Set<PayoutStatus> COMMITTED = Set.of(PENDING, APPROVED, PAID);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, PayoutStatus> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised payout status
     */
    public static Optional<PayoutStatus> tryFrom(String value) {
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
     *         blank, or not a recognised payout status
     */
    public static PayoutStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payout status is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid payout status. Allowed values are PENDING, APPROVED, REJECTED, "
                        + "or PAID."));
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
