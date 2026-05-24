package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Outcome of a single upstream AI request, recorded in {@code AiUsageLog}
 * for cost auditing and reliability dashboards.
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code AiUsageLog}. The constant set is asserted by
 * {@code SchemaEnumContractTests#finalDomainCheckConstraintsContainEveryApplicationEnumValue}
 * against the {@code chk_ai_usage_logs_status} check constraint, so any
 * change MUST be paired with a Flyway migration that updates the
 * constraint.</p>
 */
public enum AiRequestStatus {

    /** Provider returned a usable response within the timeout. */
    SUCCESS,

    /** Provider returned an error response or the response was unusable. */
    FAILED,

    /**
     * Provider could not be reached at all (network, timeout, circuit
     * breaker open, missing API key). Distinguished from {@link #FAILED}
     * so reliability metrics can isolate outages from quality issues.
     */
    UNAVAILABLE;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AiRequestStatus> LOOKUP =
            EnumLookups.indexByName(AiRequestStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised status
     */
    public static Optional<AiRequestStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised status
     */
    public static AiRequestStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI request status is required.",
                () -> "Invalid AI request status. Allowed values are SUCCESS, FAILED, or UNAVAILABLE.");
    }

    /** @return {@code true} when the request returned a usable response. */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** @return {@code true} when the request did not return a usable response. */
    public boolean isFailure() {
        return this != SUCCESS;
    }

    /** @return {@code true} when the provider could not be reached at all. */
    public boolean isUnavailable() {
        return this == UNAVAILABLE;
    }
}
