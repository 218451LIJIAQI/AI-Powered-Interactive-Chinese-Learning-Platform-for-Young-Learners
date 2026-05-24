package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public static final Set<RefundStatus> TERMINAL = Set.of(REJECTED, REFUNDED);

    /** States in which the request still requires action before becoming terminal. */
    public static final Set<RefundStatus> ACTIONABLE = Set.of(PENDING, APPROVED);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, RefundStatus> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised refund status
     */
    public static Optional<RefundStatus> tryFrom(String value) {
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
     *         blank, or not a recognised refund status
     */
    public static RefundStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refund status is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid refund status. Allowed values are PENDING, APPROVED, REJECTED, "
                        + "or REFUNDED."));
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
