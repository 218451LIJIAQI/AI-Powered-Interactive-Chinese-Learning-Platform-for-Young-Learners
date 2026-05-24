package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Admin-facing AI response report row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/ai-reports} (paginated triage
 *       queue; filterable by status)</li>
 *   <li>{@code POST /api/v1/admin/ai-reports/{id}/review} — see
 *       {@link ReviewAiResponseReportRequest}; echoes the
 *       updated row</li>
 * </ul>
 *
 * <p>Drives the admin's "AI safety triage" screen — the human
 * checkpoint for the platform's bounded-AI stance. Surfaces the
 * full report row including:</p>
 * <ul>
 *   <li>Reporter identity ({@link #studentUserId},
 *       {@link #studentName} = {@code users.display_name},
 *       {@link #studentEmail}) so admins can follow up
 *       directly.</li>
 *   <li>Underlying AI artefact pointers
 *       ({@link #aiUsageLogId}, {@link #chatSessionId},
 *       {@link #chatMessageId}) for click-through to the source
 *       record on {@link AdminAiUsageLogResponse}.</li>
 *   <li>Frozen content ({@link #promptSnapshot},
 *       {@link #aiResponseSnapshot}) — what the student actually
 *       saw, persisted on the report so later edits to the
 *       upstream message row do not lose evidence.</li>
 *   <li>Resolution audit ({@link #reviewerUserId},
 *       {@link #reviewerName}, {@link #reviewerNote},
 *       {@link #reviewedAt}) — populated only for terminal states.</li>
 * </ul>
 *
 * <p>Compared to the student-facing
 * {@link StudentAiResponseReportResponse}, this DTO retains
 * reporter identity and reviewer identity so the admin queue can
 * be sorted/filtered/triaged with full context. See
 * {@link CreateAiResponseReportRequest} for the field semantics
 * carried through from creation.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers (snapshot tests, log
 * fingerprints, frontend code that pins on field positions).</p>
 *
 * <h2>{@code toString()} redaction</h2>
 * <p>The auto-generated record {@code toString()} is overridden so
 * sensitive content — student PII ({@link #studentEmail}), the
 * student's free-text {@link #description}, the report
 * {@link #promptSnapshot} / {@link #aiResponseSnapshot} (which
 * may carry harmful AI output that triggered the report), and the
 * admin's {@link #reviewerNote} — never appear in plain log files
 * or exception traces. Each redacted field is replaced with a
 * {@code "<redacted:N>"} marker that preserves the field's
 * presence and length without exposing its content; the
 * non-sensitive id and timestamp fields are kept verbatim so the
 * log line still pinpoints the offending row.</p>
 */
@JsonPropertyOrder({
        "id",
        "studentUserId",
        "studentName",
        "studentEmail",
        "aiUsageLogId",
        "chatSessionId",
        "chatMessageId",
        "featureKey",
        "reasonCategory",
        "description",
        "promptSnapshot",
        "aiResponseSnapshot",
        "status",
        "reviewerUserId",
        "reviewerName",
        "reviewerNote",
        "reviewedAt",
        "createdAt",
        "updatedAt"
})
public record AdminAiResponseReportResponse(
        Long id,
        Long studentUserId,
        String studentName,
        String studentEmail,
        Long aiUsageLogId,
        Long chatSessionId,
        Long chatMessageId,
        String featureKey,
        String reasonCategory,
        String description,
        String promptSnapshot,
        String aiResponseSnapshot,
        String status,
        Long reviewerUserId,
        String reviewerName,
        String reviewerNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Redacts every sensitive content field so a log line that
     * accidentally captures this DTO never leaks PII or the
     * harmful AI content that triggered the report. The
     * non-sensitive id, status and timestamp fields are kept
     * verbatim so the log line still pinpoints the offending row
     * for forensic follow-up via {@link #id}.
     */
    @Override
    public String toString() {
        return "AdminAiResponseReportResponse["
                + "id=" + id
                + ", studentUserId=" + studentUserId
                + ", studentName=" + redactValue(studentName)
                + ", studentEmail=" + redactValue(studentEmail)
                + ", aiUsageLogId=" + aiUsageLogId
                + ", chatSessionId=" + chatSessionId
                + ", chatMessageId=" + chatMessageId
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", description=" + redactLength(description)
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + ", status=" + status
                + ", reviewerUserId=" + reviewerUserId
                + ", reviewerName=" + redactValue(reviewerName)
                + ", reviewerNote=" + redactLength(reviewerNote)
                + ", reviewedAt=" + reviewedAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + "]";
    }

    private static String redactValue(String value) {
        return value == null ? "null" : "<redacted>";
    }

    private static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
