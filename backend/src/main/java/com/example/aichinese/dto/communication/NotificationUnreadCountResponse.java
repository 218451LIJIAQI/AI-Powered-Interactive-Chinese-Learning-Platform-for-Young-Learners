package com.example.aichinese.dto.communication;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Unread-badge counter returned by
 * {@code GET /api/v1/notifications/unread-count}.
 *
 * <p>Drives the bell-icon badge in the top navigation across every
 * authenticated role. The front-end polls this endpoint on a short
 * interval (currently 60&nbsp;s) so the badge stays current without
 * a websocket / SSE channel; the lean single-field record shape
 * keeps each poll's payload negligible.</p>
 *
 * <p>The counter is produced by
 * {@code NotificationService.countAccessibleUnreadNotifications},
 * which excludes any unread notifications whose underlying target is
 * no longer reachable by the calling user — so the badge cannot get
 * "stuck" pointing to a deleted lesson or revoked enrolment.</p>
 *
 * <h2>Related endpoints</h2>
 * <ul>
 *   <li>The full inbox list is served by {@code GET /api/v1/notifications}
 *       as {@link NotificationResponse}.</li>
 *   <li>The bulk mark-read operation {@code PATCH /api/v1/notifications/read-all}
 *       returns {@link BulkOperationResponse}.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>{@link #unreadCount} is a primitive {@code long} so the JSON
 * wire format always emits a numeric value (zero is the common
 * steady-state case). The component order is locked via
 * {@link JsonPropertyOrder} so future field additions cannot
 * reshuffle existing positions.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #unreadCount} is always {@code >= 0}; the canonical
 *       constructor rejects negative values to surface producer
 *       bugs at the call site (a count is, by construction, a
 *       non-negative cardinal number).</li>
 * </ul>
 *
 * @param unreadCount number of unread notifications visible to the
 *                    calling user; always {@code >= 0}
 */
@JsonPropertyOrder({ "unreadCount" })
public record NotificationUnreadCountResponse(
        long unreadCount) {

    /**
     * Defensive canonical constructor: rejects negative counts which
     * would indicate a producer bug. A future repository-level
     * {@code count} query that incorrectly returns a sentinel
     * negative value would otherwise propagate silently to the
     * frontend badge.
     */
    public NotificationUnreadCountResponse {
        if (unreadCount < 0L) {
            throw new IllegalArgumentException(
                    "unreadCount must be non-negative but was " + unreadCount);
        }
    }
}
