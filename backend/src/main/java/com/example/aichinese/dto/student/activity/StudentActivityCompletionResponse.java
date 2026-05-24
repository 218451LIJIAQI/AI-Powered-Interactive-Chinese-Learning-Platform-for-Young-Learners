package com.example.aichinese.dto.student.activity;

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
 * @param activityId       id of the activity that was completed,
 *                         echoed for client-side reconciliation
 * @param type             discriminator for which of the six activity
 *                         tables the row came from
 * @param earnedPoints     XP awarded by THIS request; {@code 0} on
 *                         non-first completions
 * @param totalPoints      student's running total XP after applying
 *                         {@link #earnedPoints}
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
}
