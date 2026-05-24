package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Type of supporting document a teacher uploads during verification.
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code TeacherVerificationDocument}. The constant set is asserted by
 * {@code SchemaEnumContractTests#finalDomainCheckConstraintsContainEveryApplicationEnumValue}
 * against the {@code chk_teacher_verification_documents_type} check
 * constraint, so any change MUST be paired with a Flyway migration
 * that updates the constraint.</p>
 */
public enum TeacherVerificationDocumentType {

    /** Teaching certificate (e.g. TESOL, HSK examiner credential). */
    CERTIFICATE,

    /** Academic qualification (degree, diploma, transcript). */
    QUALIFICATION,

    /** Government-issued identification (passport, national ID). */
    IDENTITY,

    /** Catch-all for documents that do not fit the above categories. */
    OTHER;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, TeacherVerificationDocumentType> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * <p>Note: unlike {@link #fromValue(String)}, this method does
     * <em>not</em> apply the legacy "fall back to {@link #OTHER} on blank"
     * convenience. Callers that want that behaviour should use
     * {@code tryFrom(value).orElse(OTHER)} or call {@link #fromValue(String)}
     * directly.</p>
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised document type
     */
    public static Optional<TeacherVerificationDocumentType> tryFrom(String value) {
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
     * Lenient parse that the existing {@code TeacherManagementService}
     * relies on.
     *
     * <p>Behaviour preserved from the original implementation:</p>
     * <ul>
     *   <li>{@code null} or blank input falls back to {@link #OTHER}
     *       rather than throwing — this is intentional so that legacy
     *       upload flows that omit the document type still succeed.</li>
     *   <li>Non-blank input that does not match any constant throws
     *       "{@code Invalid document type. Allowed values are CERTIFICATE,
     *       QUALIFICATION, IDENTITY, or OTHER.}".</li>
     * </ul>
     *
     * @throws IllegalArgumentException when {@code value} is non-blank
     *         but does not name a recognised document type
     */
    public static TeacherVerificationDocumentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid document type. Allowed values are CERTIFICATE, "
                        + "QUALIFICATION, IDENTITY, or OTHER."));
    }

    /** @return {@code true} when this document is a teaching certificate. */
    public boolean isCertificate() {
        return this == CERTIFICATE;
    }

    /** @return {@code true} when this document is an academic qualification. */
    public boolean isQualification() {
        return this == QUALIFICATION;
    }

    /** @return {@code true} when this document is government-issued identification. */
    public boolean isIdentity() {
        return this == IDENTITY;
    }
}
