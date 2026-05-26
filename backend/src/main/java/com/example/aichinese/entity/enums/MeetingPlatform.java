package com.example.aichinese.entity.enums;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Third-party video conferencing platform that hosts a {@code LiveClass}.
 *
 * <p>The teacher selects a platform when scheduling a live session; the
 * frontend uses this hint to render the correct branded "Join" button.
 * Server-side scheduling additionally uses {@link #allowsHost(String)} to
 * enforce platform-specific URL hosts before a meeting link is ever stored.
 * {@link #OTHER} is a safe default for platforms that are not yet first-class
 * supported and accepts any otherwise-safe HTTP(S) host.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code LiveClass}. The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired
 * Flyway migration.</p>
 */
public enum MeetingPlatform {

    /** Zoom Meetings ({@code zoom.us}). */
    ZOOM(Set.of("zoom.us")),

    /** Google Meet ({@code meet.google.com}). */
    GOOGLE_MEET(Set.of("meet.google.com")),

    /** Microsoft Teams ({@code teams.microsoft.com}). */
    MICROSOFT_TEAMS(Set.of("teams.microsoft.com", "teams.live.com")),

    /** Any other meeting platform with a generic join URL. */
    OTHER(Set.of());

    private final Set<String> allowedHostSuffixes;

    MeetingPlatform(Set<String> allowedHostSuffixes) {
        this.allowedHostSuffixes = allowedHostSuffixes;
    }

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, MeetingPlatform> LOOKUP =
            buildLookup();

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised platform
     */
    public static Optional<MeetingPlatform> tryFrom(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(normalizeLookupKey(value)));
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised platform
     */
    public static MeetingPlatform fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Meeting platform is required.");
        }
        return tryFrom(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid meeting platform. Allowed values are ZOOM, GOOGLE_MEET, "
                                + "MICROSOFT_TEAMS, or OTHER."));
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

    /**
     * Checks whether a validated HTTP(S) meeting URL host belongs to this
     * platform. Subdomains are accepted for providers that commonly issue
     * tenant-specific hosts (for example {@code us02web.zoom.us}).
     */
    public boolean allowsHost(String host) {
        if (this == OTHER) {
            return true;
        }
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.strip().toLowerCase(Locale.ROOT);
        return allowedHostSuffixes.stream()
                .anyMatch(suffix -> normalizedHost.equals(suffix) || normalizedHost.endsWith("." + suffix));
    }

    private static Map<String, MeetingPlatform> buildLookup() {
        Map<String, MeetingPlatform> lookup = new HashMap<>();
        for (MeetingPlatform platform : values()) {
            lookup.put(normalizeLookupKey(platform.name()), platform);
        }
        lookup.put(normalizeLookupKey("Zoom"), ZOOM);
        lookup.put(normalizeLookupKey("Google Meet"), GOOGLE_MEET);
        lookup.put(normalizeLookupKey("Meet"), GOOGLE_MEET);
        lookup.put(normalizeLookupKey("Microsoft Teams"), MICROSOFT_TEAMS);
        lookup.put(normalizeLookupKey("MS Teams"), MICROSOFT_TEAMS);
        lookup.put(normalizeLookupKey("Teams"), MICROSOFT_TEAMS);
        return Map.copyOf(lookup);
    }

    private static String normalizeLookupKey(String value) {
        return value.strip()
                .replaceAll("[\\s-]+", "_")
                .toUpperCase(Locale.ROOT);
    }
}
