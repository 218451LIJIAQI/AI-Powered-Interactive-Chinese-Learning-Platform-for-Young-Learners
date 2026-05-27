package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Aggregate teacher home dashboard payload returned by
 * {@code GET /api/v1/teachers/dashboard}.
 *
 * <p>Drives the teacher landing page on login — bundles the
 * teacher's catalogue footprint, student engagement, earnings
 * snapshot, and latest signals (quiz attempts on their content,
 * notifications) into one round trip. The complementary
 * {@link com.example.aichinese.dto.analytics.TeacherAnalyticsResponse}
 * is the chart-heavy follow-up.</p>
 *
 * <h2>{@link #teacherStatus}</h2>
 * <p>The teacher's current verification state
 * ({@link TeacherStatus}). When not {@link TeacherStatus#APPROVED}
 * the dashboard shows a verification banner and most authoring
 * actions are gated. The status here is the canonical platform-side
 * state and matches the one on
 * {@code TeacherProfileResponse} /
 * {@code TeacherVerificationStatusResponse}.</p>
 *
 * <h2>Headline counters</h2>
 * <ul>
 *   <li>{@link #totalLessons} — count of teacher's authored
 *       lessons (across all statuses).</li>
 *   <li>{@link #totalStudents} — count of distinct students who
 *       have engaged with any of the teacher's content.</li>
 *   <li>{@link #averageQuizScore} — mean score percentage across
 *       all attempts on the teacher's quizzes
 *       ({@link BigDecimal} 0-100).</li>
 * </ul>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #earningsSummary} — same four totals as on
 *       {@code TeacherEarningSummaryResponse}, surfaced here so
 *       the dashboard renders without a follow-up call.</li>
 *   <li>{@link #recentQuizResults} — most-recent attempts on the
 *       teacher's quizzes (across all students); the embedded
 *       {@link RecentQuizResult} adds
 *       {@link RecentQuizResult#studentName} (the
 *       {@link com.example.aichinese.entity.User#getDisplayName()})
 *       which the student-facing
 *       {@link StudentDashboardResponse.RecentQuizResult} does not
 *       carry — teachers care about who attempted, students do not.</li>
 *   <li>{@link #recentNotifications} — same shape as
 *       {@link StudentDashboardResponse.NotificationSummary}; the
 *       per-role inboxes feed it.</li>
 * </ul>
 *
 * <p>{@link #fullName} is the teacher's {@code users.display_name}
 * (historical naming carries through — see
 * {@code TeacherStudentSummaryResponse#fullName}).</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>{@link #teacherStatus} and {@link #earningsSummary} are
 *       never {@code null}.</li>
 *   <li>{@link #averageQuizScore} is never {@code null}; the
 *       canonical constructor substitutes {@link BigDecimal#ZERO}
 *       when the producer passes {@code null} (typical for a
 *       brand-new teacher with no quiz attempts yet).</li>
 *   <li>{@link #recentQuizResults} and {@link #recentNotifications}
 *       are never {@code null} (empty list when no rows match) and
 *       are wrapped via
 *       {@link List#copyOf(java.util.Collection)} so the DTO is
 *       fully immutable and safe to share across requests.</li>
 *   <li>{@link #fullName} may be {@code null} when the teacher's
 *       account has no display name set.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} on
 * the outer record and on every embedded inner record so upstream
 * Jackson upgrades cannot accidentally reshuffle the wire format
 * and break consumers that pin on field positions.</p>
 *
 * @param fullName            personalised greeting source
 *                            ({@code users.display_name}); may be
 *                            {@code null}
 * @param teacherStatus       canonical teacher verification state;
 *                            never {@code null}
 * @param totalLessons        count of teacher-authored
 *                            {@link com.example.aichinese.entity.Lesson}
 *                            rows
 * @param totalStudents       count of distinct students who have
 *                            engaged with the teacher's content
 * @param averageQuizScore    0-100 percentage average across
 *                            attempts on the teacher's quizzes;
 *                            never {@code null} (defaults to
 *                            {@link BigDecimal#ZERO})
 * @param earningsSummary     four-total earnings snapshot; never
 *                            {@code null}
 * @param recentQuizResults   most-recent quiz attempts on the
 *                            teacher's quizzes, newest first;
 *                            unmodifiable and never {@code null}
 * @param recentNotifications top-N unread / recent notifications;
 *                            unmodifiable and never {@code null}
 */
@JsonPropertyOrder({
        "fullName",
        "teacherStatus",
        "totalLessons",
        "totalStudents",
        "averageQuizScore",
        "earningsSummary",
        "recentQuizResults",
        "recentNotifications"
})
public record TeacherDashboardResponse(
        String fullName,
        TeacherStatus teacherStatus,
        long totalLessons,
        long totalStudents,
        BigDecimal averageQuizScore,
        EarningsSummary earningsSummary,
        List<RecentQuizResult> recentQuizResults,
        List<NotificationSummary> recentNotifications) {

    /**
     * Defensive canonical constructor: rejects {@code null}
     * non-list components, substitutes {@link BigDecimal#ZERO}
     * for a {@code null} {@link #averageQuizScore}, and wraps
     * every embedded list with
     * {@link List#copyOf(java.util.Collection) List.copyOf} so the
     * resulting DTO is fully immutable.
     */
    public TeacherDashboardResponse {
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        Objects.requireNonNull(earningsSummary, "earningsSummary must not be null");
        if (averageQuizScore == null) {
            averageQuizScore = BigDecimal.ZERO;
        }
        recentQuizResults = List.copyOf(
                Objects.requireNonNull(recentQuizResults, "recentQuizResults must not be null"));
        recentNotifications = List.copyOf(
                Objects.requireNonNull(recentNotifications,
                        "recentNotifications must not be null"));
    }

    /**
     * Earnings snapshot mirroring the four totals on
     * {@code TeacherEarningSummaryResponse}. Surfaced inline here
     * so the home dashboard can show "Available: RM 320" without
     * hitting the dedicated earnings endpoint.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>Every monetary field is never {@code null}; the
     *       canonical constructor substitutes
     *       {@link BigDecimal#ZERO} when the producer passes
     *       {@code null} so the frontend never has to branch on a
     *       missing aggregate (typical for a brand-new teacher with
     *       no earnings yet).</li>
     * </ul>
     *
     * <h2>Wire format</h2>
     * <p>The component order is locked via {@link JsonPropertyOrder}.</p>
     *
     * @param grossAmount       cumulative gross earnings before
     *                          platform commission, in the platform
     *                          base currency; never {@code null}
     * @param netAmount         cumulative net earnings (after
     *                          commission); never {@code null}
     * @param availableBalance  currently withdrawable balance;
     *                          never {@code null}
     * @param pendingBalance    earnings pending payout / settlement;
     *                          never {@code null}
     */
    @JsonPropertyOrder({
            "grossAmount",
            "netAmount",
            "availableBalance",
            "pendingBalance",
            "currencyCode"
    })
    public record EarningsSummary(
            BigDecimal grossAmount,
            BigDecimal netAmount,
            BigDecimal availableBalance,
            BigDecimal pendingBalance,
            String currencyCode) {

        /**
         * Defensive canonical constructor: substitutes
         * {@link BigDecimal#ZERO} for any {@code null} monetary
         * aggregate so the frontend never has to branch on a
         * missing value when rendering earnings stat tiles.
         */
        public EarningsSummary {
            if (grossAmount == null) {
                grossAmount = BigDecimal.ZERO;
            }
            if (netAmount == null) {
                netAmount = BigDecimal.ZERO;
            }
            if (availableBalance == null) {
                availableBalance = BigDecimal.ZERO;
            }
            if (pendingBalance == null) {
                pendingBalance = BigDecimal.ZERO;
            }
        }
    }

    /**
     * Trimmed quiz-attempt row for the teacher's "Recent attempts
     * on my quizzes" widget. Adds {@link #studentName} (which the
     * student-facing equivalent does not carry) so teachers can
     * see who attempted at a glance.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>{@link #attemptId} is never {@code null} (primary
     *       key).</li>
     *   <li>{@link #quizTitle}, {@link #studentName},
     *       {@link #score}, {@link #maxScore},
     *       {@link #completedAt} may individually be {@code null}
     *       for legacy / partially-loaded attempts.</li>
     * </ul>
     *
     * @param attemptId    primary key of the
     *                     {@link com.example.aichinese.entity.QuizAttempt};
     *                     never {@code null}
     * @param quizTitle    display title of the quiz; may be
     *                     {@code null}
     * @param studentName  display name of the attempting student;
     *                     may be {@code null}
     * @param score        points earned on this attempt; may be
     *                     {@code null}
     * @param maxScore     maximum points the attempt could have
     *                     earned; may be {@code null}
     * @param completedAt  timestamp the attempt was finalised;
     *                     may be {@code null} for attempts still
     *                     in progress
     */
    @JsonPropertyOrder({
            "attemptId",
            "quizTitle",
            "studentName",
            "score",
            "maxScore",
            "completedAt"
    })
    public record RecentQuizResult(
            Long attemptId,
            String quizTitle,
            String studentName,
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
     * Trimmed notification row, structurally identical to
     * {@link StudentDashboardResponse.NotificationSummary}; both
     * roles share the same dashboard widget renderer. Kept as a
     * separate inner record so the role-scoped dashboards remain
     * independently versionable without coupling their wire
     * formats.
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
