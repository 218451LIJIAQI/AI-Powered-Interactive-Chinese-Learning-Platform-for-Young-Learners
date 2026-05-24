package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Commercial access tier of a learning artefact (lesson, module, package).
 *
 * <p>Used by the entitlement layer to decide whether a student needs an
 * active purchase or subscription before consuming the content. Persisted
 * with {@code @Enumerated(EnumType.STRING)} on entities such as
 * {@code Lesson} and {@code LearningModule}, so the constant names form
 * part of the database contract — they MUST NOT be renamed or removed
 * without a paired Flyway migration.</p>
 */
public enum AccessType {

    /** Free-of-charge content available to every authenticated student. */
    FREE,

    /** Paid content that requires an active entitlement (purchase or subscription). */
    PAID;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AccessType> LOOKUP = EnumLookups.indexByName(AccessType.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when {@code value}
     *         is {@code null}, blank, or not a recognised access type
     */
    public static Optional<AccessType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @param value raw textual representation
     * @return the matching constant
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised access type
     */
    public static AccessType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Access type is required.",
                () -> "Invalid access type. Allowed values are FREE or PAID.");
    }

    /** @return {@code true} when this access tier requires no payment. */
    public boolean isFree() {
        return this == FREE;
    }

    /** @return {@code true} when this access tier requires a paid entitlement. */
    public boolean isPaid() {
        return this == PAID;
    }
}
