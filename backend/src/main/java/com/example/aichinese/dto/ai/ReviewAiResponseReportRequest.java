package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/ai-reports/{id}/review} —
 * the admin disposes of an AI response report opened by a student
 * via {@link CreateAiResponseReportRequest}.
 *
 * <p>The review action records a transition into a terminal state
 * with the admin's mandatory note. Both fields
 * ({@link #status}, {@link #reviewerNote}) are required so the
 * student receives a substantive resolution, not a silent close.</p>
 *
 * <h2>{@link #status}</h2>
 * <p>Target review state — string form of the report status
 * vocabulary ({@code "RESOLVED"} = admin agrees and has acted,
 * {@code "DISMISSED"} = admin reviewed but found no issue,
 * {@code "ESCALATED"} = needs senior review). Capped at 30 chars
 * to match the schema column.</p>
 *
 * <h2>{@link #reviewerNote}</h2>
 * <p>Required free-text rationale shown to the reporting student
 * on {@link StudentAiResponseReportResponse}. Sanitised
 * server-side; capped at 1000 chars.</p>
 */
public record ReviewAiResponseReportRequest(
        @NotBlank(message = "Review status is required.")
        @Size(max = 30, message = "Review status must be 30 characters or fewer.")
        String status,

        @NotBlank(message = "Reviewer note is required.")
        @Size(max = 1000, message = "Reviewer note must be 1000 characters or fewer.")
        String reviewerNote) {
}
