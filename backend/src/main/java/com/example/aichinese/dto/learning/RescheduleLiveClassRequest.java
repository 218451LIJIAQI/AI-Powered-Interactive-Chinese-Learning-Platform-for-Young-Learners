package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotNull;

/**
 * Body for
 * {@code PATCH /api/v1/teachers/live-classes/{id}/reschedule} — the
 * teacher moves an upcoming live class to a new slot.
 *
 * <p>This is the dedicated reschedule path; doing the same edit
 * through the general {@link UpdateLiveClassRequest} would force
 * the teacher to re-supply title, meeting link, etc. The server
 * additionally:</p>
 * <ul>
 *   <li>Validates the new times via
 *       {@code LiveLearningAccessService.validateLiveClassTiming}
 *       ({@link #endTime} must be strictly after
 *       {@link #startTime}).</li>
 *   <li>Auto-flips the status to
 *       {@link com.example.aichinese.entity.enums.LiveClassStatus#RESCHEDULED}
 *       so enrolled students see the change reflected on the
 *       student-facing card.</li>
 *   <li>Fans out a "Live class rescheduled" notification to every
 *       enrolled student.</li>
 * </ul>
 *
 * <p>Both fields are required; partial reschedules are not
 * supported (clients always send the full new pair).</p>
 *
 * <p>Note: this DTO intentionally does <em>not</em> annotate
 * {@link #startTime} with {@link jakarta.validation.constraints.Future @Future}
 * because the teacher may legitimately reschedule a session that
 * was already moved into the past (e.g. recovering from a
 * mistakenly-cancelled session whose original slot lay in the
 * past). The temporal ordering check (end &gt; start) is enforced
 * server-side regardless.</p>
 *
 * @param startTime new wall-clock instant the session begins
 *                  (Asia/Singapore zone); required
 * @param endTime   new wall-clock instant the session ends
 *                  (Asia/Singapore zone); required and must be
 *                  strictly after {@link #startTime}
 */
@JsonPropertyOrder({ "startTime", "endTime" })
public record RescheduleLiveClassRequest(
        @NotNull(message = "Start time is required.")
        LocalDateTime startTime,

        @NotNull(message = "End time is required.")
        LocalDateTime endTime) {
}
