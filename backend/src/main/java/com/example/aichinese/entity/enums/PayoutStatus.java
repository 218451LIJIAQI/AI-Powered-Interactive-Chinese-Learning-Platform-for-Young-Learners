package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum PayoutStatus {

    PENDING,

    APPROVED,

    REJECTED,

    PAID;

    public static final Set<PayoutStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(REJECTED, PAID));

    public static final Set<PayoutStatus> COMMITTED =
            Collections.unmodifiableSet(EnumSet.of(PENDING, APPROVED, PAID));

    public static final Set<PayoutStatus> IN_FLIGHT =
            Collections.unmodifiableSet(EnumSet.of(PENDING, APPROVED));

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

    private static final Map<String, PayoutStatus> LOOKUP =
            EnumLookups.indexByName(PayoutStatus.class);

    public static Optional<PayoutStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PayoutStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Payout status is required.",
                () -> "Invalid payout status. Allowed values are PENDING, APPROVED, REJECTED, "
                        + "or PAID.");
    }
}
