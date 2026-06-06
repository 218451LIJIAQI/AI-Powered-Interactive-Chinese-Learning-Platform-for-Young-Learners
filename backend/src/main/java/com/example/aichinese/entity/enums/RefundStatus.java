package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum RefundStatus {

    PENDING,

    APPROVED,

    REJECTED,

    REFUNDED;

    public static final Set<RefundStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(REJECTED, REFUNDED));

    public static final Set<RefundStatus> ACTIONABLE =
            Collections.unmodifiableSet(EnumSet.of(PENDING, APPROVED));

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

    private static final Map<String, RefundStatus> LOOKUP =
            EnumLookups.indexByName(RefundStatus.class);

    public static Optional<RefundStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static RefundStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Refund status is required.",
                () -> "Invalid refund status. Allowed values are PENDING, APPROVED, REJECTED, "
                        + "or REFUNDED.");
    }
}
