package com.example.aichinese.dto.learning;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Max;
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
 * keep stored rows internally consistent. The 0–100 bound is
 * enforced at the validation layer so an obviously-malformed
 * heartbeat (e.g. {@code 9999}) is rejected with {@code 400 Bad
 * Request} rather than silently coerced by the recompute step.</p>
 *
 * @param watchedSeconds     accumulated seconds the student has
 *                           watched; non-negative; {@code null} =
 *                           no change
 * @param totalSeconds       full duration of the recording in
 *                           seconds; non-negative; {@code null} =
 *                           no change
 * @param progressPercentage caller-supplied 0–100 percent (the
 *                           server recomputes the canonical value);
 *                           {@code null} = no change
 * @param completed          explicit completion flag; {@code null} =
 *                           no change
 */
@JsonPropertyOrder({ "watchedSeconds", "totalSeconds", "progressPercentage", "completed" })
public record VideoProgressRequest(
        @PositiveOrZero(message = "Watched seconds must not be negative.")
        Integer watchedSeconds,

        @PositiveOrZero(message = "Total seconds must not be negative.")
        Integer totalSeconds,

        @PositiveOrZero(message = "Progress percentage must not be negative.")
        @Max(value = 100, message = "Progress percentage must not exceed 100.")
        Integer progressPercentage,

        Boolean completed) {
}
