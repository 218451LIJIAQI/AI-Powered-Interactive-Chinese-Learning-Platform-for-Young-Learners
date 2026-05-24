package com.example.aichinese.dto.communication;

/**
 * Lean summary returned by bulk-mutation endpoints that update
 * multiple rows in a single call. Currently used by:
 * <ul>
 *   <li>{@code POST /api/v1/notifications/mark-all-read} —
 *       marks every unread notification of the calling user as
 *       read in one transaction; returns the count of rows
 *       actually flipped.</li>
 * </ul>
 *
 * <p>Returning just a count keeps the payload tiny — the front-end
 * does not need the per-row state, and refetching the unread badge
 * via {@link NotificationUnreadCountResponse} is cheap enough that
 * the bulk endpoint does not echo the full list.</p>
 *
 * <p>{@link #updatedCount} is a primitive {@code long} so the JSON
 * wire format always emits a numeric value (zero is a legitimate
 * "no rows matched" response — e.g. when the user calls mark-all-
 * read but nothing was unread).</p>
 */
public record BulkOperationResponse(
        long updatedCount) {
}
