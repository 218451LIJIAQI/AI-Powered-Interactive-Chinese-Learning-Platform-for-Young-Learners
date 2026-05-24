package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-student analytics returned by
 * {@code GET /api/v1/students/analytics} — the calling student's
 * own learning telemetry.
 *
 * <p>Drives the student-side "My progress" analytics page. Distinct
 * from the lighter {@code StudentDashboardResponse} used by the
 * home dashboard — this DTO is chart-heavy and assumes the user
 * has explicitly navigated to the analytics tab.</p>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #quizScoreTrend} — average quiz-score percentage
 *       per attempt-day over the recent window, oldest first
 *       ({@link AnalyticsTrendPointResponse} carrying a
 *       {@link BigDecimal} percentage).</li>
 *   <li>{@link #activityTrend} — daily count of any tracked
 *       learning activity (lesson view, video watch, quiz attempt)
 *       used for the "study consistency" line.</li>
 *   <li>{@link #lessonStatusBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.LessonStatus}
 *       (e.g. {@code NOT_STARTED} / {@code IN_PROGRESS} /
 *       {@code COMPLETED}).</li>
 *   <li>{@link #weakAreaBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.TeacherFeedbackType}
 *       across the student's
 *       {@link com.example.aichinese.entity.TeacherFeedback} rows.
 *       Same source as
 *       {@code StudentProgressSummaryResponse#weakAreas()}, but
 *       returned with counts here so the chart can size each
 *       segment.</li>
 *   <li>{@link #learningModeBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.LearningType}
 *       showing the student's preferred learning modality.</li>
 * </ul>
 *
 * <h2>{@link Overview}</h2>
 * <p>Headline counters surfaced as the top stat tiles: completed
 * and in-progress lesson counts, total tracked lessons, average
 * quiz score, completed video count, upcoming live-class count and
 * unread-notification count. {@link Overview#averageQuizScore()}
 * is a 0-100 percentage carried as {@link BigDecimal} so JSON
 * serialisation cannot drop fractional precision.</p>
 *
 * <p>Companion DTOs: {@link AdminAnalyticsResponse} (platform
 * roll-up) and {@link TeacherAnalyticsResponse} (teacher cohort
 * roll-up) share the embedded
 * {@link AnalyticsBreakdownItemResponse} /
 * {@link AnalyticsTrendPointResponse} primitives.</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>{@link #overview} is never {@code null}.</li>
 *   <li>Each embedded list is never {@code null} (empty list when
 *       a backing query returned no rows) and is wrapped via
 *       {@link List#copyOf(java.util.Collection)} so the DTO is
 *       fully immutable and safe to share across requests.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} on
 * both the outer record and the embedded {@link Overview} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param overview                headline KPI block; never
 *                                {@code null}
 * @param quizScoreTrend          daily average quiz-score
 *                                percentage trend, oldest first;
 *                                unmodifiable and never
 *                                {@code null}
 * @param activityTrend           daily learning-activity count
 *                                trend, oldest first; unmodifiable
 *                                and never {@code null}
 * @param lessonStatusBreakdown   count of student's tracked
 *                                lessons by
 *                                {@link com.example.aichinese.entity.enums.LessonStatus};
 *                                unmodifiable and never
 *                                {@code null}
 * @param weakAreaBreakdown       count of received teacher
 *                                feedback by
 *                                {@link com.example.aichinese.entity.enums.TeacherFeedbackType};
 *                                unmodifiable and never
 *                                {@code null}
 * @param learningModeBreakdown   count of completed lesson views
 *                                by
 *                                {@link com.example.aichinese.entity.enums.LearningType};
 *                                unmodifiable and never
 *                                {@code null}
 */
@JsonPropertyOrder({
        "overview",
        "quizScoreTrend",
        "activityTrend",
        "lessonStatusBreakdown",
        "weakAreaBreakdown",
        "learningModeBreakdown"
})
public record StudentAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> activityTrend,
        List<AnalyticsBreakdownItemResponse> lessonStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> weakAreaBreakdown,
        List<AnalyticsBreakdownItemResponse> learningModeBreakdown) {

    /**
     * Defensive canonical constructor: rejects {@code null}
     * components and wraps every embedded list with
     * {@link List#copyOf(java.util.Collection) List.copyOf} so the
     * resulting DTO is fully immutable.
     * {@link List#copyOf(java.util.Collection) List.copyOf} is
     * cheap when the input is already an unmodifiable list (the
     * typical {@code Stream.toList()} case in the analytics
     * service).
     */
    public StudentAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        quizScoreTrend = List.copyOf(
                Objects.requireNonNull(quizScoreTrend, "quizScoreTrend must not be null"));
        activityTrend = List.copyOf(
                Objects.requireNonNull(activityTrend, "activityTrend must not be null"));
        lessonStatusBreakdown = List.copyOf(
                Objects.requireNonNull(lessonStatusBreakdown,
                        "lessonStatusBreakdown must not be null"));
        weakAreaBreakdown = List.copyOf(
                Objects.requireNonNull(weakAreaBreakdown, "weakAreaBreakdown must not be null"));
        learningModeBreakdown = List.copyOf(
                Objects.requireNonNull(learningModeBreakdown,
                        "learningModeBreakdown must not be null"));
    }

    /**
     * Headline KPI block for the student analytics page.
     * {@link #averageQuizScore} is a 0-100 percentage carried as
     * {@link BigDecimal} so JSON serialisation cannot lose
     * fractional precision.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>All count fields are primitive {@code long} and
     *       therefore always emitted as numeric JSON values.</li>
     *   <li>{@link #averageQuizScore} is never {@code null}; the
     *       canonical constructor substitutes
     *       {@link BigDecimal#ZERO} when the producer passes
     *       {@code null} (e.g. a student with no quiz attempts so
     *       far) so the frontend never has to branch on a missing
     *       average.</li>
     * </ul>
     *
     * <h2>Wire format</h2>
     * <p>The component order is locked via {@link JsonPropertyOrder}.</p>
     *
     * @param completedLessons       count of lessons the student
     *                               has marked complete
     * @param inProgressLessons      count of lessons the student
     *                               has started but not yet
     *                               completed
     * @param totalTrackedLessons    total lessons tracked under
     *                               this student's progress
     *                               (denominator for the two
     *                               counts above)
     * @param averageQuizScore       0-100 percentage average across
     *                               all the student's quiz
     *                               attempts; never {@code null}
     *                               (defaults to
     *                               {@link BigDecimal#ZERO})
     * @param completedVideos        count of recorded-lesson videos
     *                               watched to completion
     * @param upcomingLiveClasses    count of live classes scheduled
     *                               in the future the student is
     *                               enrolled in
     * @param unreadNotifications    unread notifications for the
     *                               calling student (drives the
     *                               bell-icon badge on this page)
     */
    @JsonPropertyOrder({
            "completedLessons",
            "inProgressLessons",
            "totalTrackedLessons",
            "averageQuizScore",
            "completedVideos",
            "upcomingLiveClasses",
            "unreadNotifications"
    })
    public record Overview(
            long completedLessons,
            long inProgressLessons,
            long totalTrackedLessons,
            BigDecimal averageQuizScore,
            long completedVideos,
            long upcomingLiveClasses,
            long unreadNotifications) {

        /**
         * Defensive canonical constructor: substitutes
         * {@link BigDecimal#ZERO} for a {@code null}
         * {@link #averageQuizScore} so the frontend never has to
         * branch on a missing percentage when rendering the
         * average-score gauge.
         */
        public Overview {
            if (averageQuizScore == null) {
                averageQuizScore = BigDecimal.ZERO;
            }
        }
    }
}
