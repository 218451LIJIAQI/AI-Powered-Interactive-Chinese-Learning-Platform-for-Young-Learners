package com.example.aichinese.dto.communication;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Lean summary returned by bulk-mutation endpoints that update
 * multiple rows in a single call. Currently used by:
 * <ul>
 *   <li>{@code PATCH /api/v1/notifications/read-all} —
 *       marks every accessible unread notification of the calling
 *       user as read in one transaction; returns the count of rows
 *       actually flipped (notifications whose underlying target is
 *       no longer accessible are skipped and excluded from the
 *       count).</li>
 * </ul>
 *
 * <p>Returning just a count keeps the payload tiny — the frontend
 * does not need the per-row state, and refetching the unread badge
 * via {@link NotificationUnreadCountResponse} is cheap enough that
 * the bulk endpoint does not echo the full list.</p>
 *
 * <h2>Wire format</h2>
 * <p>{@link #updatedCount} is a primitive {@code long} so the JSON
 * wire format always emits a numeric value (zero is a legitimate
 * "no rows matched" response — e.g. when the user calls
 * {@code read-all} but nothing was unread, or every unread row was
 * filtered out by accessibility checks). The component order is
 * locked via {@link JsonPropertyOrder} so future field additions
 * cannot reshuffle existing positions.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #updatedCount} is always {@code >= 0}; the canonical
 *       constructor rejects negative values to surface programming
 *       errors at the call site.</li>
 * </ul>
 *
 * @param updatedCount number of rows actually mutated by the bulk
 *                     operation; always {@code >= 0}
 */
@JsonPropertyOrder({ "updatedCount" })
public record BulkOperationResponse(
        long updatedCount) {

    /**
     * Defensive canonical constructor: rejects negative counts which
     * would indicate a producer bug (a count is, by construction, a
     * non-negative cardinal number).
     */
    public BulkOperationResponse {
        if (updatedCount < 0L) {
            throw new IllegalArgumentException(
                    "updatedCount must be non-negative but was " + updatedCount);
        }
    }
}
