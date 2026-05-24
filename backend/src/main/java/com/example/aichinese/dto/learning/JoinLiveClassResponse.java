package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #liveClassId} and {@link #title} are non-{@code null}
 *       — they are the columns the join screen renders
 *       unconditionally and the deep-link uses.</li>
 *   <li>{@link #meetingPlatform} and {@link #meetingLink} may be
 *       {@code null} only as a degenerate guard — the underlying
 *       {@link com.example.aichinese.entity.LiveClass} schema
 *       defaults the platform to
 *       {@link com.example.aichinese.entity.enums.MeetingPlatform#OTHER}
 *       and the join service refuses to issue this payload when
 *       the link is missing — but the DTO is permissive so a
 *       degenerate persisted row still serialises rather than
 *       throwing on read.</li>
 * </ul>
 *
 * @param liveClassId     identifier of the
 *                        {@link com.example.aichinese.entity.LiveClass}
 *                        the student is joining; never {@code null}
 * @param title           short human-readable session title for
 *                        front-end "Joining {title}…" copy; never
 *                        {@code null}
 * @param meetingPlatform enum-name string (e.g. {@code "ZOOM"});
 *                        nominally non-null, defensively permitted
 *                        to be {@code null} for legacy data
 * @param meetingLink     full {@code http(s)://} URL the front-end
 *                        opens in a new tab; nominally non-null,
 *                        defensively permitted to be {@code null}
 *                        for legacy data
 */
@JsonPropertyOrder({ "liveClassId", "title", "meetingPlatform", "meetingLink" })
public record JoinLiveClassResponse(
        Long liveClassId,
        String title,
        String meetingPlatform,
        String meetingLink) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity and display fields the front-end "Join meeting"
     * screen renders unconditionally. {@link #meetingPlatform} and
     * {@link #meetingLink} are intentionally permitted to be
     * {@code null} per the invariants documented above so the DTO
     * can serialise a degenerate persisted row (the join service
     * already refuses to call this constructor in that state, but
     * the DTO must still be safe to construct).
     */
    public JoinLiveClassResponse {
        Objects.requireNonNull(liveClassId, "liveClassId must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }
}
