package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lifecycle state of a student-submitted {@code AiResponseReport}.
 *
 * <p>State machine:</p>
 *
 * <pre>
 *      PENDING_REVIEW ─▶ IN_REVIEW ─┬─▶ RESOLVED   (terminal)
 *                                   └─▶ DISMISSED  (terminal)
 * </pre>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code AiResponseReport}. The constant set is asserted by
 * {@code SchemaEnumContractTests#finalDomainCheckConstraintsContainEveryApplicationEnumValue}
 * against the {@code chk_ai_response_reports_status} check constraint,
 * so any change MUST be paired with a Flyway migration that updates the
 * constraint.</p>
 */
public enum AiResponseReportStatus {

    /** Newly submitted; not yet picked up by a moderator. */
    PENDING_REVIEW,

    /** A moderator has claimed the report and is investigating. */
    IN_REVIEW,

    /** Moderator confirmed the issue and applied a remediation. */
    RESOLVED,

    /** Moderator concluded the report did not require action. */
    DISMISSED;

    /** Statuses in which the report still requires moderator attention. */
    public static final Set<AiResponseReportStatus> OPEN =
            Collections.unmodifiableSet(EnumSet.of(PENDING_REVIEW, IN_REVIEW));

    /** Statuses in which the report has reached a terminal state. */
    public static final Set<AiResponseReportStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(RESOLVED, DISMISSED));

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AiResponseReportStatus> LOOKUP =
            EnumLookups.indexByName(AiResponseReportStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised status
     */
    public static Optional<AiResponseReportStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised status
     */
    public static AiResponseReportStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI report status is required.",
                () -> "Invalid AI report status. Allowed values are PENDING_REVIEW, IN_REVIEW, "
                        + "RESOLVED, or DISMISSED.");
    }

    /** @return {@code true} when the report still requires moderator attention. */
    public boolean isOpen() {
        return OPEN.contains(this);
    }

    /** @return {@code true} when the report has reached a terminal state. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** @return {@code true} when the moderator confirmed and remediated the issue. */
    public boolean isResolved() {
        return this == RESOLVED;
    }
}
