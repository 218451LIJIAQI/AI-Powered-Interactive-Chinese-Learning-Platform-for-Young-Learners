package com.example.aichinese.dto.communication;

/**
 * Unread-badge counter returned by
 * {@code GET /api/v1/notifications/unread-count}.
 *
 * <p>Drives the bell-icon badge in the top navigation across all
 * roles. Polled by the front-end on a short interval (currently
 * 60 s) so the badge stays current without a websocket / SSE
 * channel; the lean record shape keeps poll cost negligible.</p>
 *
 * <p>{@link #unreadCount} is a primitive {@code long} (zero is
 * the common case once the user clears their inbox; the JSON
 * wire format always emits a numeric value).</p>
 *
 * <p>The full inbox list lives behind
 * {@link NotificationResponse}; bulk mark-read returns
 * {@link BulkOperationResponse}.</p>
 */
public record NotificationUnreadCountResponse(
        long unreadCount) {
}
