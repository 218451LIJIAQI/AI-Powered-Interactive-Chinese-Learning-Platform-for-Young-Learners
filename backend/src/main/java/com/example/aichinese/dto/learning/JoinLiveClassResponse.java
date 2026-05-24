package com.example.aichinese.dto.learning;

/**
 * Lean payload returned by
 * {@code GET /api/v1/students/live-classes/{id}/join} — the
 * "claim my meeting link" endpoint.
 *
 * <p>This is the <em>only</em> endpoint that always emits
 * {@link #meetingLink}, regardless of whether
 * {@link LiveClassResponse#meetingLinkVisible()} would have been
 * {@code true} on the general detail endpoint. Access is gated by
 * {@code LiveLearningAccessService.ensureStudentCanJoin}, which
 * raises a 403 when the student is not enrolled or the class is
 * not in {@code SCHEDULED}/{@code ONGOING} state.</p>
 *
 * <p>Each successful call is recorded as an audit-log entry so the
 * teacher can see who actually clicked through to their session.
 * The front-end opens {@link #meetingLink} in a new tab and does
 * not retain it client-side beyond that one window.</p>
 *
 * <p>{@link #meetingPlatform} is the
 * {@link com.example.aichinese.entity.enums.MeetingPlatform}
 * constant name as a string ("ZOOM", "GOOGLE_MEET",
 * "MICROSOFT_TEAMS", etc.) so the front-end can pick the right
 * branding/icon.</p>
 */
public record JoinLiveClassResponse(
        Long liveClassId,
        String title,
        String meetingPlatform,
        String meetingLink) {
}
