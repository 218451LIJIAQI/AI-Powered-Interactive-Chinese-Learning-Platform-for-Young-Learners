package com.example.aichinese.dto.teacher;

import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Lean verification-state payload returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/verification-status} — read</li>
 *   <li>{@code POST /api/v1/teachers/verification-documents} — after
 *       a multipart document upload</li>
 *   <li>{@code POST /api/v1/teachers/resubmit-verification} — after
 *       flipping a {@link TeacherStatus#REJECTED} profile back to
 *       {@link TeacherStatus#PENDING}</li>
 * </ul>
 *
 * <p>Drives the dashboard "verification banner" widget. Returning the
 * same shape from the upload and resubmit endpoints lets the front-end
 * refresh the banner without making a second GET — uploading a new
 * document might or might not flip the status (it stays
 * {@link TeacherStatus#PENDING} during admin review), so the client
 * always re-renders from the response.</p>
 *
 * <p>Compared to {@link TeacherProfileResponse}, this DTO drops the
 * editable identity/bio fields (irrelevant to the verification widget)
 * and adds two pieces only meaningful here:</p>
 * <ul>
 *   <li>{@link #latestReviewResult} — the most recent admin decision
 *       row from {@code teacher_verification_reviews}, surfaced so the
 *       teacher can see who reviewed the application and when.</li>
 *   <li>{@link #resubmissionAllowed} — derived server-side from
 *       {@link TeacherStatus#canResubmit()} (currently {@code true}
 *       only for {@link TeacherStatus#REJECTED}). Drives whether the
 *       front-end shows the "Resubmit" button.</li>
 * </ul>
 *
 * <h2>{@link #latestSubmittedDocuments}</h2>
 * <p>Filtered to <em>only</em> the latest version of each document
 * type (i.e. each entry has
 * {@link TeacherVerificationDocumentSummaryResponse#currentVersion()}
 * {@code == true}), unlike
 * {@link TeacherProfileResponse#verificationDocuments()} which
 * additionally retains historical versions for audit. This keeps the
 * dashboard widget compact.</p>
 *
 * @param teacherStatus              current verification state
 * @param latestSubmittedDocuments   one row per document type (current
 *                                   version only)
 * @param latestReviewResult         most recent admin decision;
 *                                   {@code null} when the profile has
 *                                   not yet been reviewed
 * @param rejectionReason            free-text reason from the latest
 *                                   {@link TeacherStatus#REJECTED}
 *                                   decision; {@code null} otherwise
 * @param resubmissionAllowed        whether the "Resubmit" button
 *                                   should be enabled; primitive
 *                                   {@code boolean} so the wire format
 *                                   always emits a value
 */
public record TeacherVerificationStatusResponse(
        TeacherStatus teacherStatus,
        List<TeacherVerificationDocumentSummaryResponse> latestSubmittedDocuments,
        TeacherVerificationReviewSummaryResponse latestReviewResult,
        String rejectionReason,
        boolean resubmissionAllowed) {
}
