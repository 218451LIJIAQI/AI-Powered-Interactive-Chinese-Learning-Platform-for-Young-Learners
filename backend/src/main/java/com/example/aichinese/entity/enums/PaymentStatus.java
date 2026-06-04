package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum PaymentStatus {

    PENDING,

    PAID,

    FAILED,

    CANCELLED,

    REFUNDED;

    public static final Set<PaymentStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(PAID, FAILED, CANCELLED, REFUNDED));

    public static final Set<PaymentStatus> GRANTS_ENTITLEMENT =
            Collections.unmodifiableSet(EnumSet.of(PAID));

    static {
        EnumSet<PaymentStatus> terminal = EnumSet.copyOf(TERMINAL);
        EnumSet<PaymentStatus> expectedTerminal = EnumSet.complementOf(EnumSet.of(PENDING));
        if (!terminal.equals(expectedTerminal)) {
            EnumSet<PaymentStatus> missing = EnumSet.copyOf(expectedTerminal);
            missing.removeAll(terminal);
            EnumSet<PaymentStatus> extra = EnumSet.copyOf(terminal);
            extra.removeAll(expectedTerminal);
            throw new AssertionError(
                    "PaymentStatus TERMINAL must equal ALL \\ {PENDING}; missing=" + missing
                            + ", extra=" + extra);
        }

        EnumSet<PaymentStatus> grants = EnumSet.copyOf(GRANTS_ENTITLEMENT);
        if (!terminal.containsAll(grants)) {
            EnumSet<PaymentStatus> nonTerminalGrants = EnumSet.copyOf(grants);
            nonTerminalGrants.removeAll(terminal);
            throw new AssertionError(
                    "PaymentStatus GRANTS_ENTITLEMENT must be a subset of TERMINAL; "
                            + "transient-yet-granting: " + nonTerminalGrants);
        }
        if (grants.contains(PENDING)) {
            throw new AssertionError(
                    "PaymentStatus PENDING must not be present in GRANTS_ENTITLEMENT — "
                            + "entitlements must never be issued on an unconfirmed payment.");
        }
    }

    private static final Map<String, PaymentStatus> LOOKUP =
            EnumLookups.indexByName(PaymentStatus.class);

    public static Optional<PaymentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PaymentStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Payment status is required.",
                () -> "Invalid payment status. Allowed values are PENDING, PAID, FAILED, "
                        + "CANCELLED, or REFUNDED.");
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isPaid() {
        return this == PAID;
    }

    public boolean isRefunded() {
        return this == REFUNDED;
    }

    public boolean isUnpaidTerminal() {
        return this == FAILED || this == CANCELLED;
    }

    public boolean canBeRefunded() {
        return this == PAID;
    }
}
