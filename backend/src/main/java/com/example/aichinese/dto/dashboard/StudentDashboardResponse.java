package com.example.aichinese.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Aggregate student home dashboard payload returned by
 * {@code GET /api/v1/students/dashboard}.
 *
 * <p>Drives the student landing page on login. Single-shot
 * snapshot of "what should I do next" — recent quiz results,
 * unfinished lessons, notifications, and a server-recommended next
 * step. The complementary
 * {@link com.example.aichinese.dto.analytics.StudentAnalyticsResponse}
 * is the chart-heavy follow-up.</p>
 *
 * <h2>Headline counters</h2>
 * <ul>
 *   <li>{@link #learningProgressPercent} — overall completion
 *       across all started lessons (0-100). Mirrors
 *       {@code StudentProgressSummaryResponse#overallCompletionPercent()}.</li>
 *   <li>{@link #dailyStreak} — current learning streak in days
 *       (Asia/Singapore calendar). Same as
 *       {@code DailyStreakResponse#currentStreak()}.</li>
 *   <li>{@link #points} — total XP. Mirrors
 *       {@code RewardSummaryResponse#totalPoints()}.</li>
 *   <li>{@link #badgeCount} — number of badges earned. Primitive
 *       {@code long} (other counters are {@code int} because they
 *       represent capped percentages or modest counts).</li>
 * </ul>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #recentQuizResults} — most-recent quiz attempts;
 *       {@link RecentQuizResult} is a trimmed view of
 *       {@code QuizAttemptResultResponse} that intentionally
 *       drops the per-question breakdown.</li>
 *   <li>{@link #weakAreas} — same {@code feedbackType} aggregation
 *       documented on
 *       {@code StudentProgressSummaryResponse#weakAreas()};
 *       front-end drives the "Focus on" panel.</li>
 *   <li>{@link #unfinishedLessons} — most-recent in-progress
 *       lessons sorted by last access; drives the "Continue
 *       learning" prompt.</li>
 *   <li>{@link #recentNotifications} — top-N unread / recent
 *       notifications; embedded {@link NotificationSummary} is a
 *       trimmed view of
 *       {@link com.example.aichinese.dto.communication.NotificationResponse}.</li>
 *   <li>{@link #recommendedNextStep} — pre-formatted human label
 *       (e.g. "Continue Lesson 3", "Review your wrong answers")
 *       computed server-side from the student's current state so
 *       the front-end displays one canonical CTA.</li>
 * </ul>
 *
 * <p>{@link #fullName} is the student's display name (the
 * historical naming carries through — see the note on
 * {@code TeacherStudentSummaryResponse#fullName}).</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>Each embedded list is never {@code null} (empty list when
 *       no rows match) and is wrapped via
 *       {@link List#copyOf(java.util.Collection)} so the DTO is
 *       fully immutable and safe to share across requests.</li>
 *   <li>{@link #fullName}, {@link #currentLevel} and
 *       {@link #recommendedNextStep} may be {@code null} when the
 *       producer has nothing to surface.</li>
 *   <li>The percentage / streak / points fields are primitive and
 *       therefore always emitted as numeric JSON values.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * on the outer record and on every embedded inner record so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param fullName                 personalised greeting source
 *                                 ({@code users.display_name});
 *                                 may be {@code null}
 * @param currentLevel             label of the student's current
 *                                 placement / proficiency tier;
 *                                 may be {@code null}
 * @param learningProgressPercent  overall lesson completion
 *                                 percentage 0-100
 * @param dailyStreak              current learning-streak length
 *                                 in days
 * @param points                   total XP earned
 * @param badgeCount               total badges earned
 * @param recentQuizResults        most-recent quiz attempts,
 *                                 newest first; unmodifiable and
 *                                 never {@code null}
 * @param weakAreas                feedback-type tags representing
 *                                 the student's weak areas;
 *                                 unmodifiable and never
 *                                 {@code null}
 * @param unfinishedLessons        in-progress lessons sorted by
 *                                 last access; unmodifiable and
 *                                 never {@code null}
 * @param recentNotifications      top-N unread / recent
 *                                 notifications; unmodifiable and
 *                                 never {@code null}
 * @param recommendedNextStep      server-computed canonical CTA;
 *                                 may be {@code null} when the
 *                                 student has no obvious next
 *                                 step
 */
@JsonPropertyOrder({
        "fullName",
        "currentLevel",
        "learningProgressPercent",
        "dailyStreak",
        "points",
        "badgeCount",
        "recentQuizResults",
        "weakAreas",
        "unfinishedLessons",
        "recentNotifications",
        "recommendedNextStep"
})
public record StudentDashboardResponse(
        String fullName,
        String currentLevel,
        int learningProgressPercent,
        int dailyStreak,
        int points,
        long badgeCount,
        List<RecentQuizResult> recentQuizResults,
        List<String> weakAreas,
        List<LessonProgressSummary> unfinishedLessons,
        List<NotificationSummary> recentNotifications,
        String recommendedNextStep) {

    /**
     * Defensive canonical constructor: wraps every embedded list
     * with {@link List#copyOf(java.util.Collection) List.copyOf}
     * so the resulting DTO is fully immutable.
     * {@link List#copyOf(java.util.Collection) List.copyOf} is
     * cheap when the input is already an unmodifiable list (the
     * typical {@code Stream.toList()} case in
     * {@code DashboardService}).
     */
    public StudentDashboardResponse {
        recentQuizResults = List.copyOf(
                Objects.requireNonNull(recentQuizResults, "recentQuizResults must not be null"));
        weakAreas = List.copyOf(
                Objects.requireNonNull(weakAreas, "weakAreas must not be null"));
        unfinishedLessons = List.copyOf(
                Objects.requireNonNull(unfinishedLessons, "unfinishedLessons must not be null"));
        recentNotifications = List.copyOf(
                Objects.requireNonNull(recentNotifications,
                        "recentNotifications must not be null"));
    }

    /**
     * Trimmed quiz-result row for the dashboard's "Recent quizzes"
     * widget. Does not include the per-question breakdown that
     * {@code QuizAttemptResultResponse} carries — clicking through
     * to the attempt detail screen yields that.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>{@link #attemptId} is never {@code null} (primary
     *       key).</li>
     *   <li>{@link #quizId}, {@link #quizTitle},
     *       {@link #completedAt}, {@link #score}, {@link #maxScore}
     *       may individually be {@code null} for legacy /
     *       partially-loaded attempts.</li>
     * </ul>
     *
     * @param attemptId     primary key of the
     *                      {@link com.example.aichinese.entity.QuizAttempt};
     *                      never {@code null}
     * @param quizId        primary key of the
     *                      {@link com.example.aichinese.entity.Quiz};
     *                      may be {@code null}
     * @param quizTitle     display title of the quiz; may be
     *                      {@code null}
     * @param score         points earned on this attempt; may be
     *                      {@code null} on attempts that did not
     *                      record a score
     * @param maxScore      maximum points the attempt could have
     *                      earned; may be {@code null}
     * @param completedAt   timestamp the attempt was finalised;
     *                      may be {@code null} for attempts still
     *                      in progress
     */
    @JsonPropertyOrder({
            "attemptId",
            "quizId",
            "quizTitle",
            "score",
            "maxScore",
            "completedAt"
    })
    public record RecentQuizResult(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Integer score,
            Integer maxScore,
            LocalDateTime completedAt) {

        /**
         * Defensive canonical constructor: enforces non-null on
         * the persistence-guaranteed primary key.
         */
        public RecentQuizResult {
            Objects.requireNonNull(attemptId, "attemptId must not be null");
        }
    }

    /**
     * Compact lesson-progress row for the "Continue learning"
     * widget. Server orders by {@link #lastAccessedAt} {@code DESC}
     * so the most-recently-touched lesson surfaces first;
     * completed lessons (100%) are excluded from this list.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>{@link #lessonId} is never {@code null}.</li>
     *   <li>{@link #lessonTitle} / {@link #completionPercent} /
     *       {@link #lastAccessedAt} may be {@code null} for
     *       partially-loaded rows.</li>
     * </ul>
     *
     * @param lessonId          primary key of the
     *                          {@link com.example.aichinese.entity.Lesson};
     *                          never {@code null}
     * @param lessonTitle       display title of the lesson; may be
     *                          {@code null}
     * @param completionPercent 0-100 completion percentage; may
     *                          be {@code null}
     * @param lastAccessedAt    timestamp the student last touched
     *                          this lesson; may be {@code null}
     */
    @JsonPropertyOrder({
            "lessonId",
            "lessonTitle",
            "completionPercent",
            "lastAccessedAt"
    })
    public record LessonProgressSummary(
            Long lessonId,
            String lessonTitle,
            Integer completionPercent,
            LocalDateTime lastAccessedAt) {

        /**
         * Defensive canonical constructor: enforces non-null on
         * the persistence-guaranteed primary key.
         */
        public LessonProgressSummary {
            Objects.requireNonNull(lessonId, "lessonId must not be null");
        }
    }

    /**
     * Trimmed notification row for the dashboard inbox preview.
     * Drops the {@code type}, {@code targetType}, {@code targetId},
     * and {@code readAt} fields surfaced on the full
     * {@link com.example.aichinese.dto.communication.NotificationResponse};
     * the dashboard widget only needs enough to render a one-line
     * preview with a click-through path.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>{@link #notificationId}, {@link #title},
     *       {@link #message}, {@link #status} and
     *       {@link #createdAt} are never {@code null} — they map
     *       to the primary key, the {@code NOT NULL} columns, and
     *       the managed timestamp on
     *       {@link com.example.aichinese.entity.base.BaseEntity}.</li>
     *   <li>{@link #targetPath} may be {@code null} when the
     *       notification has no destination.</li>
     * </ul>
     *
     * @param notificationId primary key of the
     *                       {@link com.example.aichinese.entity.Notification};
     *                       never {@code null}
     * @param title          short headline shown in the inbox row;
     *                       never {@code null}
     * @param message        notification body text; never
     *                       {@code null}
     * @param status         {@code .name()} of
     *                       {@link com.example.aichinese.entity.enums.NotificationStatus}
     *                       ({@code "UNREAD"} or {@code "READ"});
     *                       never {@code null}
     * @param targetPath     pre-formatted front-end route to
     *                       deep-link the user to; may be
     *                       {@code null}
     * @param createdAt      timestamp the notification was queued
     *                       for the recipient; never {@code null}
     */
    @JsonPropertyOrder({
            "notificationId",
            "title",
            "message",
            "status",
            "targetPath",
            "createdAt"
    })
    public record NotificationSummary(
            Long notificationId,
            String title,
            String message,
            String status,
            String targetPath,
            LocalDateTime createdAt) {

        /**
         * Defensive canonical constructor: enforces non-null on
         * the fields the persistence layer unconditionally
         * guarantees.
         */
        public NotificationSummary {
            Objects.requireNonNull(notificationId, "notificationId must not be null");
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(message, "message must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }
}
