package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lifecycle state of a single teacher earning record.
 *
 * <p>Each {@code TeacherEarning} row is created in the {@link #AVAILABLE}
 * state when the underlying order is paid, and is flipped to
 * {@link #REFUNDED} when the associated order is fully refunded so the
 * amount is excluded from the teacher's withdrawable balance.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code TeacherEarning}. The constant set is asserted by
 * {@code SchemaEnumContractTests#finalDomainCheckConstraintsContainEveryApplicationEnumValue}
 * against the {@code chk_teacher_earnings_status} check constraint, so
 * any change to the constant set MUST be paired with a Flyway migration
 * that updates the constraint.</p>
 */
public enum TeacherEarningStatus {

    /** Earning has been credited to the teacher and is eligible for payout. */
    AVAILABLE,

    /** Earning has been clawed back because the underlying order was refunded. */
    REFUNDED;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, TeacherEarningStatus> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised status
     */
    public static Optional<TeacherEarningStatus> tryFrom(String value) {
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
     *         blank, or not a recognised status
     */
    public static TeacherEarningStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Teacher earning status is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid teacher earning status. Allowed values are AVAILABLE or REFUNDED."));
    }

    /** @return {@code true} when the earning may contribute to the payout balance. */
    public boolean isAvailable() {
        return this == AVAILABLE;
    }

    /** @return {@code true} when the earning has been clawed back. */
    public boolean isRefunded() {
        return this == REFUNDED;
    }
}
