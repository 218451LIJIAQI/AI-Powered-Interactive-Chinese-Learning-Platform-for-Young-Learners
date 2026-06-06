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

}
