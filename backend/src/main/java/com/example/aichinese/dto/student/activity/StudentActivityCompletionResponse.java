package com.example.aichinese.dto.student.activity;

import java.util.Objects;

import com.example.aichinese.dto.admin.activity.ActivityType;

/**
 * Server response for the six platform-activity completion endpoints
 * under {@code POST /api/v1/students/activities/{path}/{id}/complete}
 * (one path per {@link ActivityType}).
 *
 * <p>Tells the student-facing UI how many points were just earned, the
 * new running total, and whether this was the first time this activity
 * counted towards the student's XP — so the UI can show a celebration
 * animation only on the very first successful completion.</p>
 *
 * <h2>Idempotency / replay semantics</h2>
 * <p>The complete-activity service ({@code completePlatformActivity}
 * on {@code StudentLearningProgressService}) only awards XP the first
 * time a given (student, activity-type, activity-id) tuple is recorded.
 * Subsequent replays still return {@code 200 OK} with this DTO, but
 * with {@link #earnedPoints} {@code == 0} and
 * {@link #firstCompletion} {@code == false}; {@link #totalPoints}
 * still reflects the student's current running XP. This lets clients
 * call complete unconditionally without needing to track replay state
 * locally.</p>
 *
 * <h2>Why primitive {@code int} / {@code boolean}</h2>
 * <p>These three fields are deliberately primitive (not boxed) so the
 * JSON wire format always emits a value — there is no null/false
 * ambiguity for the front-end celebration trigger or the XP counter.
 * {@link #activityId} is boxed because it carries through whatever id
 * the path produced and is never expected to be missing for a
 * successful response.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor fails fast on contract violations so a
 * malformed response never reaches the wire format:</p>
 * <ul>
 *   <li>{@link #activityId} and {@link #type} must be non-{@code null}
 *       — they are echoed for client-side reconciliation and the front
 *       end indexes by both.</li>
 *   <li>{@link #earnedPoints} and {@link #totalPoints} must be
 *       non-negative — XP is a strictly accumulating counter.</li>
 *   <li>If {@link #firstCompletion} is {@code false} (a replay), then
 *       {@link #earnedPoints} must be {@code 0} — encodes the
 *       idempotency rule of {@code completePlatformActivity} so a
 *       service-layer regression that double-awards XP fails loudly
 *       at the DTO boundary instead of silently corrupting the wire
 *       payload.</li>
 * </ul>
 *
 * <h2>Construction</h2>
 * <p>Service-layer code should prefer the named factories
 * {@link #firstCompletion(Long, ActivityType, int, int)} and
 * {@link #replay(Long, ActivityType, int)} over the canonical
 * constructor — they make the celebration vs. replay intent obvious at
 * the call site and remove the chance of mis-pairing
 * {@code firstCompletion} with a non-zero {@code earnedPoints}.</p>
 *
 * @param activityId       id of the activity that was completed,
 *                         echoed for client-side reconciliation;
 *                         never {@code null}
 * @param type             discriminator for which of the six activity
 *                         tables the row came from; never {@code null}
 * @param earnedPoints     XP awarded by THIS request; {@code 0} on
 *                         non-first completions; non-negative
 * @param totalPoints      student's running total XP after applying
 *                         {@link #earnedPoints}; non-negative
 * @param firstCompletion  {@code true} only on the first successful
 *                         completion of this activity by this student;
 *                         drives celebration UI
 */
public record StudentActivityCompletionResponse(
        Long activityId,
        ActivityType type,
        int earnedPoints,
        int totalPoints,
        boolean firstCompletion) {

    /**
     * Defensive canonical constructor: enforces the non-null,
     * non-negative and idempotency invariants documented in the class
     * Javadoc. Fails fast with {@link NullPointerException} or
     * {@link IllegalArgumentException} on contract violations so a
     * malformed response cannot reach the wire format and corrupt the
     * front-end XP counter.
     */
    public StudentActivityCompletionResponse {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (earnedPoints < 0) {
            throw new IllegalArgumentException("earnedPoints must be non-negative, got " + earnedPoints);
        }
        if (totalPoints < 0) {
            throw new IllegalArgumentException("totalPoints must be non-negative, got " + totalPoints);
        }
        if (!firstCompletion && earnedPoints != 0) {
            throw new IllegalArgumentException(
                    "earnedPoints must be 0 on a non-first completion (replay), got " + earnedPoints);
        }
    }

    /**
     * Build a response for the first successful completion of an
     * activity by this student — i.e. the path that should trigger the
     * celebration UI. {@code earnedPoints} may legitimately be
     * {@code 0} when the underlying activity has a zero XP reward, so
     * this factory does not require a positive value.
     *
     * @param activityId    id of the completed activity; non-{@code null}
     * @param type          discriminator for the activity table
     * @param earnedPoints  XP awarded by this completion; non-negative
     * @param totalPoints   running XP total after the award;
     *                      non-negative
     * @return a freshly built response with
     *         {@code firstCompletion = true}
     */
    public static StudentActivityCompletionResponse firstCompletion(
            Long activityId,
            ActivityType type,
            int earnedPoints,
            int totalPoints) {
        return new StudentActivityCompletionResponse(activityId, type, earnedPoints, totalPoints, true);
    }

    /**
     * Build a response for a replay (the student has already completed
     * this activity at least once, so no XP is awarded). The factory
     * pins {@code earnedPoints = 0} as a typed shorthand for the
     * idempotency rule and prevents callers from accidentally building
     * a contract-violating "replay with reward" response.
     *
     * @param activityId   id of the completed activity; non-{@code null}
     * @param type         discriminator for the activity table
     * @param totalPoints  running XP total; non-negative and unchanged
     *                     by this call
     * @return a freshly built response with {@code earnedPoints = 0}
     *         and {@code firstCompletion = false}
     */
    public static StudentActivityCompletionResponse replay(
            Long activityId,
            ActivityType type,
            int totalPoints) {
        return new StudentActivityCompletionResponse(activityId, type, 0, totalPoints, false);
    }
}
