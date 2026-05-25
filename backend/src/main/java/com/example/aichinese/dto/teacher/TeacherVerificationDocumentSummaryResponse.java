package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Single uploaded verification artefact, surfaced inside both
 * {@link TeacherProfileResponse#verificationDocuments()} and
 * {@link TeacherVerificationStatusResponse#latestSubmittedDocuments()}.
 *
 * <p>The bytes themselves are not on the wire - only the metadata.
 * The teacher's own download endpoint is
 * {@code GET /api/v1/teachers/verification-documents/{id}}, which
 * returns the file content with the original
 * {@link #contentType} / {@link #originalFileName} as the
 * {@code Content-Disposition: attachment} response.</p>
 *
 * <h2>Version semantics</h2>
 * <p>Each {@link #documentType} (e.g. {@code "ID_CARD"},
 * {@code "TEACHING_CERTIFICATE"}) has at most one
 * {@link #currentVersion} {@code == true} row at a time. When a
 * teacher re-uploads a document of the same type, the previous row
 * is preserved with {@code currentVersion = false} (for the audit
 * trail) and the new row becomes the current version. This is why
 * {@link TeacherVerificationStatusResponse} filters to
 * {@code currentVersion == true} only - the dashboard banner shows
 * "what is on file right now" - while {@link TeacherProfileResponse}
 * keeps the full history.</p>
 *
 * <p>{@link #fileSize} is reported in bytes when available; the
 * front-end formats it as KB/MB on display. Primitive {@code boolean}
 * for {@link #currentVersion} means the JSON wire format always emits
 * a value with no null/false ambiguity.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces the persistence contract:</p>
 * <ul>
 *   <li>{@link #id}, {@link #documentType} and
 *       {@link #submittedAt} are required. They are guaranteed by
 *       {@code TeacherManagementMapper.toDocumentSummary} for any
 *       persisted document row.</li>
 *   <li>{@link #originalFileName}, {@link #contentType} and
 *       {@link #fileSize} remain nullable for legacy rows whose
 *       upload metadata was not captured.</li>
 *   <li>{@link #fileSize} must be non-negative when present. A
 *       negative byte count is structurally meaningless and would
 *       confuse the front-end formatter.</li>
 *   <li>{@link #description} remains nullable because it is the
 *       teacher-supplied free-text caption at upload time.</li>
 * </ul>
 *
 * @param id                document row id; needed for the download
 *                          URL path parameter; never {@code null}
 * @param documentType      free-form taxonomy label (e.g.
 *                          {@code "ID_CARD"},
 *                          {@code "TEACHING_CERTIFICATE"}); never
 *                          {@code null}
 * @param description       optional free-text caption supplied by the
 *                          teacher at upload time; nullable
 * @param originalFileName  filename as the teacher uploaded it;
 *                          nullable for legacy rows
 * @param contentType       MIME type from the multipart upload;
 *                          nullable for legacy rows
 * @param fileSize          raw byte size; nullable for legacy rows;
 *                          non-negative when present
 * @param submittedAt       upload timestamp; never {@code null}
 * @param currentVersion    {@code true} when this is the latest
 *                          version of {@link #documentType} on file
 */
@JsonPropertyOrder({
        "id",
        "documentType",
        "description",
        "originalFileName",
        "contentType",
        "fileSize",
        "submittedAt",
        "currentVersion"
})
public record TeacherVerificationDocumentSummaryResponse(
        Long id,
        String documentType,
        String description,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime submittedAt,
        boolean currentVersion) {

    /**
     * Defensive canonical constructor: enforces non-null on the document
     * identity fields and rejects negative file sizes so malformed metadata
     * cannot reach the wire format and confuse front-end download handling.
     */
    public TeacherVerificationDocumentSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        if (fileSize != null && fileSize < 0L) {
            throw new IllegalArgumentException("fileSize must be non-negative, got " + fileSize);
        }
    }
}
