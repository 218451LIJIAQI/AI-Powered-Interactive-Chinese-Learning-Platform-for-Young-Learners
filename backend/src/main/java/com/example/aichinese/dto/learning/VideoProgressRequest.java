package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body for the heartbeat-style video progress endpoints under
 * {@code /api/v1/students/video-progress/{videoId}}:
 * <ul>
 *   <li>{@code POST} — initial save</li>
 *   <li>{@code PUT} — periodic update</li>
 * </ul>
 *
 * <p>Both endpoints map to the same service method
 * ({@code VideoProgressService.upsertProgress}) so the front-end can
 * fire-and-forget without distinguishing first save from later
 * updates. The {@code POST .../complete} path is the dedicated
 * "mark as completed" shortcut and does not consume this DTO.</p>
 *
 * <h2>Why every field is nullable</h2>
 * <p>All four fields are nullable on purpose: the heartbeat may
 * carry only the watched-seconds delta during normal playback,
 * only the completion flag at the end, or all four during a player
 * resume. The service treats {@code null} as "no change" for that
 * field — see {@code VideoProgressService.upsertProgress} for the
 * merge logic.</p>
 *
 * <p>{@link #completed} is boxed {@link Boolean} (not primitive) so
 * the {@code null} = "no change" convention applies to it as well;
 * a literal {@code false} would explicitly un-complete the video,
 * which the service tolerates but the UI never sends.</p>
 *
 * <p>{@link #progressPercentage} is permitted but redundant with
 * {@link #watchedSeconds}/{@link #totalSeconds}; the service
 * recomputes it server-side and persists the canonical value to
 * keep stored rows internally consistent.</p>
 */
public record VideoProgressRequest(
        @PositiveOrZero(message = "Watched seconds must not be negative.")
        Integer watchedSeconds,

        @PositiveOrZero(message = "Total seconds must not be negative.")
        Integer totalSeconds,

        @PositiveOrZero(message = "Progress percentage must not be negative.")
        Integer progressPercentage,

        Boolean completed) {
}
