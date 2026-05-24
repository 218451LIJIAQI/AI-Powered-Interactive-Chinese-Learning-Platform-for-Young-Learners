package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

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
 */
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
}
