package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lifecycle state of an {@code Order} or {@code PaymentRecord}.
 *
 * <p>The platform models payments as a finite state machine:</p>
 *
 * <pre>
 *      PENDING ─┬─▶ PAID ─────▶ REFUNDED
 *               ├─▶ FAILED        (terminal)
 *               └─▶ CANCELLED     (terminal)
 * </pre>
 *
 * <p>Only {@link #PENDING} is transient — every other state is terminal.
 * The provider polling and Stripe webhook services rely on this invariant
 * (see {@code PaymentService.verifyPaymentStatus} and
 * {@code PaymentServiceTests}).</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code Order} and {@code PaymentRecord}. The constant names are part
 * of the database contract — they MUST NOT be renamed or removed without
 * a paired Flyway migration.</p>
 */
public enum PaymentStatus {

    /** Awaiting confirmation from the payment provider; the only mutable state. */
    PENDING,

    /** Provider confirmed the funds were captured; entitlements may now be granted. */
    PAID,

    /** Provider reported the payment could not be completed. */
    FAILED,

    /** The buyer or system aborted the payment before completion. */
    CANCELLED,

    /** The payment was successfully captured and subsequently refunded. */
    REFUNDED;

    /**
     * States in which the payment cannot transition any further. Useful in
     * polling loops to short-circuit redundant provider lookups.
     */
    public static final Set<PaymentStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(PAID, FAILED, CANCELLED, REFUNDED));

    /** States that grant access to the purchased entitlement(s). */
    public static final Set<PaymentStatus> GRANTS_ENTITLEMENT =
            Collections.unmodifiableSet(EnumSet.of(PAID));

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, PaymentStatus> LOOKUP =
            EnumLookups.indexByName(PaymentStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised payment status
     */
    public static Optional<PaymentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised payment status
     */
    public static PaymentStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Payment status is required.",
                () -> "Invalid payment status. Allowed values are PENDING, PAID, FAILED, "
                        + "CANCELLED, or REFUNDED.");
    }

    /** @return {@code true} when the payment is still awaiting provider confirmation. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** @return {@code true} when the payment cannot transition any further. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** @return {@code true} when the captured funds entitle the buyer to access. */
    public boolean isPaid() {
        return this == PAID;
    }

    /** @return {@code true} when funds were captured then returned to the buyer. */
    public boolean isRefunded() {
        return this == REFUNDED;
    }

    /** @return {@code true} when the payment never captured funds (failed or cancelled). */
    public boolean isUnpaidTerminal() {
        return this == FAILED || this == CANCELLED;
    }

    /**
     * Domain rule: a refund can only originate from a {@link #PAID} record;
     * once a payment is {@link #REFUNDED}, {@link #FAILED}, or
     * {@link #CANCELLED} it is no longer eligible. Centralised here so the
     * business rule has a single home.
     */
    public boolean canBeRefunded() {
        return this == PAID;
    }
}
