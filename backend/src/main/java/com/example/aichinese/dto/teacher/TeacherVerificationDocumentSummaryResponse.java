package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;

/**
 * Single uploaded verification artefact, surfaced inside both
 * {@link TeacherProfileResponse#verificationDocuments()} and
 * {@link TeacherVerificationStatusResponse#latestSubmittedDocuments()}.
 *
 * <p>The bytes themselves are not on the wire — only the metadata.
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
 * {@code currentVersion == true} only — the dashboard banner shows
 * "what is on file right now" — while {@link TeacherProfileResponse}
 * keeps the full history.</p>
 *
 * <p>{@link #fileSize} is reported in bytes; the front-end formats it
 * as KB/MB on display. Primitive {@code boolean} for
 * {@link #currentVersion} so the JSON wire format always emits a
 * value (no null/false ambiguity).</p>
 *
 * @param id                document row id; needed for the download
 *                          URL path parameter
 * @param documentType      free-form taxonomy label (e.g.
 *                          {@code "ID_CARD"},
 *                          {@code "TEACHING_CERTIFICATE"})
 * @param description       optional free-text caption supplied by the
 *                          teacher at upload time
 * @param originalFileName  filename as the teacher uploaded it
 * @param contentType       MIME type from the multipart upload
 * @param fileSize          raw byte size
 * @param submittedAt       upload timestamp
 * @param currentVersion    {@code true} when this is the latest
 *                          version of {@link #documentType} on file
 */
public record TeacherVerificationDocumentSummaryResponse(
        Long id,
        String documentType,
        String description,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime submittedAt,
        boolean currentVersion) {
}
