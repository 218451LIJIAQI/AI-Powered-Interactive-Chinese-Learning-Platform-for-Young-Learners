package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-student analytics returned by
 * {@code GET /api/v1/students/analytics} — the calling student's
 * own learning telemetry.
 *
 * <p>Drives the student-side "My progress" analytics page. Distinct
 * from the lighter {@code StudentDashboardResponse} (used by the
 * home dashboard) — this DTO is chart-heavy and assumes the user
 * has explicitly navigated to the analytics tab.</p>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #quizScoreTrend} — daily average quiz score
 *       percentage over the recent window
 *       ({@link AnalyticsTrendPointResponse} as
 *       {@link BigDecimal}).</li>
 *   <li>{@link #activityTrend} — daily count of any learning
 *       activity (lesson view, video watch, quiz attempt, etc.)
 *       used for the "study consistency" line.</li>
 *   <li>{@link #lessonStatusBreakdown} — counts by lesson status
 *       (NOT_STARTED / IN_PROGRESS / COMPLETED).</li>
 *   <li>{@link #weakAreaBreakdown} — counts by feedback type
 *       across the student's
 *       {@link com.example.aichinese.entity.TeacherFeedback}
 *       rows. Same source as
 *       {@code StudentProgressSummaryResponse#weakAreas()}, but
 *       returned with counts here so the dashboard can size the
 *       chart segments.</li>
 *   <li>{@link #learningModeBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.LearningType}
 *       (VIDEO / INTERACTIVE / TEXT) showing what the student
 *       prefers.</li>
 * </ul>
 *
 * <h2>{@link Overview}</h2>
 * <p>Headline counters: completed/in-progress lesson counts,
 * average quiz score, completed video count, upcoming live class
 * count, unread notification count. The single monetary-typed
 * field is {@link Overview#averageQuizScore()} which is a
 * percentage (0-100) carried as {@link BigDecimal} for precision.</p>
 */
public record StudentAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> activityTrend,
        List<AnalyticsBreakdownItemResponse> lessonStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> weakAreaBreakdown,
        List<AnalyticsBreakdownItemResponse> learningModeBreakdown) {

    /**
     * Headline KPI block for the student analytics page.
     * {@link #averageQuizScore} is a 0-100 percentage carried as
     * {@link BigDecimal} for two-decimal precision.
     */
    public record Overview(
            long completedLessons,
            long inProgressLessons,
            long totalTrackedLessons,
            BigDecimal averageQuizScore,
            long completedVideos,
            long upcomingLiveClasses,
            long unreadNotifications) {
    }
}
