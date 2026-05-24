package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Verification state of a {@code TeacherProfile}.
 *
 * <p>State machine:</p>
 *
 * <pre>
 *      PENDING ─┬─▶ APPROVED  ─▶ (may flip back to PENDING via resubmit)
 *               └─▶ REJECTED  ─▶ (may flip back to PENDING via resubmit)
 * </pre>
 *
 * <p>Only {@link #APPROVED} teachers may publish content; the gate is
 * enforced in {@code TeacherContentAccessService.requireApprovedTeacher}.
 * A rejected teacher may resubmit their documentation, returning the
 * profile to {@link #PENDING}.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code TeacherProfile} and {@code TeacherVerificationReview}. The
 * constant names are part of the database contract — they MUST NOT be
 * renamed or removed without a paired Flyway migration.</p>
 */
public enum TeacherStatus {

    /** Newly registered or recently resubmitted; awaiting admin review. */
    PENDING,

    /** Admin verified the documentation; teacher may publish content. */
    APPROVED,

    /** Admin declined the documentation; teacher may resubmit to retry. */
    REJECTED;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, TeacherStatus> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised teacher status
     */
    public static Optional<TeacherStatus> tryFrom(String value) {
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
     *         blank, or not a recognised teacher status
     */
    public static TeacherStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Teacher status is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid teacher status. Allowed values are PENDING, APPROVED, or REJECTED."));
    }

    /** @return {@code true} when the teacher is still awaiting admin review. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** @return {@code true} when the teacher may publish content. */
    public boolean isApproved() {
        return this == APPROVED;
    }

    /** @return {@code true} when the teacher's verification was declined. */
    public boolean isRejected() {
        return this == REJECTED;
    }

    /**
     * Domain rule: a rejected teacher may resubmit verification documents
     * and return the profile to {@link #PENDING}.
     */
    public boolean canResubmit() {
        return this == REJECTED;
    }
}
