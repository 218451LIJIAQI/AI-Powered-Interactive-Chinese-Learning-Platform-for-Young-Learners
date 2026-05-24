package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Top-level authorisation role assigned to a {@code User}.
 *
 * <p>Each user has exactly one effective role at any time. The enum is
 * persisted with {@code @Enumerated(EnumType.STRING)} on the {@code Role}
 * entity (joined to {@code User} via {@code user_roles}). The constant
 * names are part of the database contract <em>and</em> form the suffix of
 * Spring Security {@code GrantedAuthority} strings (see {@link #authority()})
 * — they MUST NOT be renamed or removed without a paired Flyway migration
 * <em>and</em> a sweep of all {@code @PreAuthorize("hasRole('...')")} usages.</p>
 */
public enum RoleName {

    /** Young learner; default role for self-registered accounts. */
    STUDENT,

    /** Teacher account; must complete verification before publishing content. */
    TEACHER,

    /** Platform administrator with full moderation and configuration powers. */
    ADMIN;

    /**
     * Spring Security authority prefix. Kept here (not in a security utility
     * class) so that the role-to-authority mapping has exactly one source
     * of truth.
     */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, RoleName> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup. Replaces the inline
     * {@code RoleName.valueOf(role.trim().toUpperCase(Locale.ROOT))} idiom
     * scattered across the service layer.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised role
     */
    public static Optional<RoleName> tryFrom(String value) {
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
     *         blank, or not a recognised role
     */
    public static RoleName fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid role. Allowed values are STUDENT, TEACHER, or ADMIN."));
    }

    /**
     * @return the Spring Security authority string for this role
     *         (e.g. {@code "ROLE_STUDENT"}); useful for {@code hasAuthority(...)}
     *         expressions and {@code SimpleGrantedAuthority} construction
     */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /** @return {@code true} when this role represents an end-learner account. */
    public boolean isStudent() {
        return this == STUDENT;
    }

    /** @return {@code true} when this role represents a content-producing teacher. */
    public boolean isTeacher() {
        return this == TEACHER;
    }

    /** @return {@code true} when this role represents a platform administrator. */
    public boolean isAdmin() {
        return this == ADMIN;
    }
}
