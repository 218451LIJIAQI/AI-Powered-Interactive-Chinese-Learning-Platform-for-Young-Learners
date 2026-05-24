package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Student-facing view of one AI response report row, returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/ai-reports} (paginated; the
 *       student's own filed reports)</li>
 *   <li>{@code POST /api/v1/students/ai-reports} (echoes the
 *       freshly-created row)</li>
 * </ul>
 *
 * <p>Drives the student's "My AI feedback reports" inbox. Lets
 * the student track admin progress on issues they have flagged.
 * Compared to the admin-facing
 * {@link AdminAiResponseReportResponse}: drops reporter identity
 * (the student is the caller), drops {@code reviewerNote}'s
 * full audit trail (the note is surfaced inline as the
 * resolution message but reviewer identity is hidden), and adds
 * no extra fields beyond what the student needs for
 * transparency.</p>
 *
 * <p>{@link #status} is the current report status (PENDING /
 * RESOLVED / DISMISSED / ESCALATED). {@link #reviewedAt} is
 * populated only for terminal states; {@code null} for PENDING.</p>
 *
 * <p>Once a report is filed, it is immutable from the student's
 * side — there is no "edit" or "withdraw" endpoint. The student
 * may file multiple reports against the same AI response; admins
 * deduplicate during triage.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>{@code toString()} redaction</h2>
 * <p>The auto-generated record {@code toString()} is overridden so
 * the three free-text content fields ({@link #description},
 * {@link #promptSnapshot}, {@link #aiResponseSnapshot}) — which
 * carry the harmful AI content that triggered the report — never
 * appear in plain log files or exception traces. The
 * non-sensitive id, status and timestamp fields are kept verbatim
 * so the log line still pinpoints the offending row for forensic
 * follow-up via {@link #id}.</p>
 */
@JsonPropertyOrder({
        "id",
        "aiUsageLogId",
        "chatSessionId",
        "chatMessageId",
        "featureKey",
        "reasonCategory",
        "description",
        "promptSnapshot",
        "aiResponseSnapshot",
        "status",
        "reviewedAt",
        "createdAt",
        "updatedAt"
})
public record StudentAiResponseReportResponse(
        Long id,
        Long aiUsageLogId,
        Long chatSessionId,
        Long chatMessageId,
        String featureKey,
        String reasonCategory,
        String description,
        String promptSnapshot,
        String aiResponseSnapshot,
        String status,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Redacts the three free-text content fields so a log line
     * that accidentally captures this DTO never leaks the harmful
     * AI content that triggered the report. The non-sensitive id,
     * feature, reason, status and timestamp fields are kept
     * verbatim so the log line still pinpoints the offending row.
     */
    @Override
    public String toString() {
        return "StudentAiResponseReportResponse["
                + "id=" + id
                + ", aiUsageLogId=" + aiUsageLogId
                + ", chatSessionId=" + chatSessionId
                + ", chatMessageId=" + chatMessageId
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", description=" + redactLength(description)
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + ", status=" + status
                + ", reviewedAt=" + reviewedAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + "]";
    }

    private static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
