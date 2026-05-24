package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Third-party video conferencing platform that hosts a {@code LiveClass}.
 *
 * <p>The teacher selects a platform when scheduling a live session; the
 * frontend uses this hint to render the correct branded "Join" button and
 * to apply platform-specific URL validation. {@link #OTHER} is a safe
 * default for platforms that are not yet first-class supported.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code LiveClass}. The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired
 * Flyway migration.</p>
 */
public enum MeetingPlatform {

    /** Zoom Meetings ({@code zoom.us}). */
    ZOOM,

    /** Google Meet ({@code meet.google.com}). */
    GOOGLE_MEET,

    /** Microsoft Teams ({@code teams.microsoft.com}). */
    MICROSOFT_TEAMS,

    /** Any other meeting platform with a generic join URL. */
    OTHER;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, MeetingPlatform> LOOKUP =
            EnumLookups.indexByName(MeetingPlatform.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised platform
     */
    public static Optional<MeetingPlatform> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised platform
     */
    public static MeetingPlatform fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Meeting platform is required.",
                () -> "Invalid meeting platform. Allowed values are ZOOM, GOOGLE_MEET, "
                        + "MICROSOFT_TEAMS, or OTHER.");
    }

    /**
     * Lenient counterpart that falls back to {@link #OTHER} for unknown or
     * missing input. Suitable for scheduling flows where an unrecognised
     * provider should still be persisted rather than rejected outright.
     */
    public static MeetingPlatform fromValueOrOther(String value) {
        return tryFrom(value).orElse(OTHER);
    }

    /** @return {@code true} when the platform is one of the natively supported brands. */
    public boolean isFirstParty() {
        return this != OTHER;
    }
}
