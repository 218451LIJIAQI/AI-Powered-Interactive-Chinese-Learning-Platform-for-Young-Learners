package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly strips surrounding Unicode whitespace from
 * both fields so Bean Validation operates on the canonical form
 * (a whitespace-only {@code reviewerNote} correctly fails
 * {@link NotBlank @NotBlank} after stripping) and the service
 * layer's {@code AiInputSanitizer} pass is idempotent.
 * {@code null} is preserved so {@link NotBlank @NotBlank} renders
 * its friendly user-facing message rather than a low-level
 * {@link NullPointerException}.</p>
 *
 * <h2>{@code toString()} redaction</h2>
 * <p>The auto-generated record {@code toString()} is overridden so
 * the admin's {@link #reviewerNote} — which may reference the
 * harmful AI content that triggered the report or carry sensitive
 * moderation context — never appears in plain log files or
 * exception traces. The non-sensitive {@link #status} token is
 * kept verbatim so the log line still indicates the transition
 * direction.</p>
 */
@JsonPropertyOrder({ "status", "reviewerNote" })
public record ReviewAiResponseReportRequest(
        @NotBlank(message = "Review status is required.")
        @Size(max = 30, message = "Review status must be 30 characters or fewer.")
        String status,

        @NotBlank(message = "Reviewer note is required.")
        @Size(max = 1000, message = "Reviewer note must be 1000 characters or fewer.")
        String reviewerNote) {

    /**
     * Defensive canonical constructor: strips both fields so Bean
     * Validation operates on the canonical form and downstream
     * sanitisation is idempotent. {@code null} is preserved so
     * {@link NotBlank @NotBlank} can render its friendly
     * user-facing message rather than a low-level
     * {@link NullPointerException}.
     */
    public ReviewAiResponseReportRequest {
        status = strip(status);
        reviewerNote = strip(reviewerNote);
    }

    /**
     * Redacts the reviewer note so a log line that accidentally
     * captures this DTO never leaks moderation context. The
     * {@link #status} token is kept verbatim so the log line still
     * indicates the transition direction.
     */
    @Override
    public String toString() {
        return "ReviewAiResponseReportRequest["
                + "status=" + status
                + ", reviewerNote=" + (reviewerNote == null
                        ? "null"
                        : "<redacted:" + reviewerNote.length() + ">")
                + "]";
    }
}
