package com.example.aichinese.entity.enums;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum MeetingPlatform {

    ZOOM(Set.of("zoom.us")),

    GOOGLE_MEET(Set.of("meet.google.com")),

    MICROSOFT_TEAMS(Set.of("teams.microsoft.com", "teams.live.com")),

    OTHER(Set.of());

    private final Set<String> allowedHostSuffixes;

    MeetingPlatform(Set<String> allowedHostSuffixes) {
        this.allowedHostSuffixes = allowedHostSuffixes;
    }

    private static final Map<String, MeetingPlatform> LOOKUP =
            buildLookup();

    public static Optional<MeetingPlatform> tryFrom(String value) {
        return normalizeLookupKey(value).map(LOOKUP::get);
    }

    public static MeetingPlatform fromValue(String value) {
        String key = normalizeLookupKey(value)
                .orElseThrow(() -> new IllegalArgumentException("Meeting platform is required."));
        MeetingPlatform platform = LOOKUP.get(key);
        if (platform == null) {
            throw new IllegalArgumentException(
                    "Invalid meeting platform. Allowed values are ZOOM, GOOGLE_MEET, "
                            + "MICROSOFT_TEAMS, or OTHER.");
        }
        return platform;
    }

    public static MeetingPlatform fromValueOrOther(String value) {
        return tryFrom(value).orElse(OTHER);
    }

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
            lookup.put(normalizeLookupKey(platform.name()).orElseThrow(), platform);
        }
        lookup.put(normalizeLookupKey("Zoom").orElseThrow(), ZOOM);
        lookup.put(normalizeLookupKey("Google Meet").orElseThrow(), GOOGLE_MEET);
        lookup.put(normalizeLookupKey("Meet").orElseThrow(), GOOGLE_MEET);
        lookup.put(normalizeLookupKey("Microsoft Teams").orElseThrow(), MICROSOFT_TEAMS);
        lookup.put(normalizeLookupKey("MS Teams").orElseThrow(), MICROSOFT_TEAMS);
        lookup.put(normalizeLookupKey("Teams").orElseThrow(), MICROSOFT_TEAMS);
        return Map.copyOf(lookup);
    }

    private static Optional<String> normalizeLookupKey(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.strip()
                .replaceAll("[\\s-]+", "_")
                .toUpperCase(Locale.ROOT));
    }
}
