package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

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
 */
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
}
