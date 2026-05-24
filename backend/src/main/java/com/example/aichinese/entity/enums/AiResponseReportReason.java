package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reason category a student selected when reporting an AI assistant
 * response.
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code AiResponseReport}. The constant set is asserted by
 * {@code SchemaEnumContractTests#finalDomainCheckConstraintsContainEveryApplicationEnumValue}
 * against the {@code chk_ai_response_reports_reason_category} check
 * constraint, so any change MUST be paired with a Flyway migration that
 * updates the constraint.</p>
 */
public enum AiResponseReportReason {

    /** AI returned an answer that is factually wrong (e.g. wrong tone, wrong meaning). */
    INCORRECT_CONTENT,

    /** AI fabricated content (e.g. invented a Chinese character or vocabulary entry). */
    HALLUCINATION,

    /** AI returned content that violates the platform's safety policy. */
    UNSAFE_CONTENT,

    /** AI returned content that is unsuitable for young learners. */
    AGE_INAPPROPRIATE,

    /** Free-form reason that does not fit any of the predefined categories. */
    OTHER;

    /**
     * Reasons that constitute a child-safety concern; reports tagged with
     * any of these are escalated to admins immediately rather than routed
     * through the standard moderation queue.
     */
    public static final Set<AiResponseReportReason> CHILD_SAFETY_CRITICAL =
            Set.of(UNSAFE_CONTENT, AGE_INAPPROPRIATE);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AiResponseReportReason> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised reason
     */
    public static Optional<AiResponseReportReason> tryFrom(String value) {
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
     *         blank, or not a recognised reason
     */
    public static AiResponseReportReason fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI report reason is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid AI report reason. Allowed values are INCORRECT_CONTENT, "
                        + "HALLUCINATION, UNSAFE_CONTENT, AGE_INAPPROPRIATE, or OTHER."));
    }

    /**
     * Lenient counterpart that falls back to {@link #OTHER} when the input
     * is missing or unrecognised. Suitable for legacy import paths where
     * an absent reason should not break the request.
     */
    public static AiResponseReportReason fromValueOrOther(String value) {
        return tryFrom(value).orElse(OTHER);
    }

    /**
     * @return {@code true} when this reason warrants immediate child-safety
     *         escalation rather than routine moderation
     */
    public boolean isChildSafetyCritical() {
        return CHILD_SAFETY_CRITICAL.contains(this);
    }
}
