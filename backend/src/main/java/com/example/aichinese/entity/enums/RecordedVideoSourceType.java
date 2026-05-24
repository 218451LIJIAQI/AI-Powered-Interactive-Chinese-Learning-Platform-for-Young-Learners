package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Origin of the playable asset behind a recorded lesson video.
 *
 * <p>Drives playback security and storage decisions on the
 * {@code RecordedLessonVideo} entity: an {@link #UPLOADED_FILE} is served
 * via signed URLs from the platform's storage layer, whereas an
 * {@link #EXTERNAL_URL} is a third-party hosted asset (e.g. YouTube,
 * Vimeo) and is treated as untrusted external content for safety
 * filtering and CSP framing.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)}, so the constant
 * names are part of the database contract — they MUST NOT be renamed or
 * removed without a paired Flyway migration.</p>
 */
public enum RecordedVideoSourceType {

    /** Asset uploaded to the platform's own object storage. */
    UPLOADED_FILE,

    /** Asset hosted by an external provider and referenced by URL. */
    EXTERNAL_URL;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, RecordedVideoSourceType> LOOKUP =
            EnumLookups.indexByName(RecordedVideoSourceType.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised source type
     */
    public static Optional<RecordedVideoSourceType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised source type
     */
    public static RecordedVideoSourceType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Recorded video source type is required.",
                () -> "Invalid recorded video source type. Allowed values are UPLOADED_FILE or EXTERNAL_URL.");
    }

    /** @return {@code true} when the playable asset lives in our own storage. */
    public boolean isUploadedFile() {
        return this == UPLOADED_FILE;
    }

    /** @return {@code true} when the playable asset is hosted by a third party. */
    public boolean isExternalUrl() {
        return this == EXTERNAL_URL;
    }
}
