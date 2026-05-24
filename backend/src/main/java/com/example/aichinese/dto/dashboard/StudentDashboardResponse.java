package com.example.aichinese.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregate student home dashboard payload returned by
 * {@code GET /api/v1/students/dashboard}.
 *
 * <p>Drives the student landing page on login. Single-shot
 * snapshot of "what should I do next" — recent quiz results,
 * unfinished lessons, notifications, and a server-recommended next
 * step. The complementary {@code StudentAnalyticsResponse} is the
 * chart-heavy follow-up.</p>
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
 *       {@code QuizAttemptResultResponse} (no per-question
 *       breakdown).</li>
 *   <li>{@link #weakAreas} — same {@code feedbackType} aggregation
 *       documented on
 *       {@code StudentProgressSummaryResponse#weakAreas()};
 *       front-end drives the "Focus on" panel.</li>
 *   <li>{@link #unfinishedLessons} — most-recent in-progress
 *       lessons sorted by last access; drives the "Continue
 *       learning" prompt.</li>
 *   <li>{@link #recentNotifications} — top-N unread/recent
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
 * historical naming as documented on
 * {@code TeacherStudentSummaryResponse#fullName}).</p>
 */
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
     * Trimmed quiz result row for the dashboard's "Recent
     * quizzes" widget. Does not include the per-question
     * breakdown that
     * {@code QuizAttemptResultResponse} carries — clicking through
     * to the attempt detail screen yields that.
     */
    public record RecentQuizResult(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Integer score,
            Integer maxScore,
            LocalDateTime completedAt) {
    }

    /**
     * Compact lesson-progress row for the "Continue learning"
     * widget. Server orders by {@link #lastAccessedAt} {@code DESC}
     * so the most-recently-touched lesson surfaces first;
     * completed lessons (100%) are excluded from this list.
     */
    public record LessonProgressSummary(
            Long lessonId,
            String lessonTitle,
            Integer completionPercent,
            LocalDateTime lastAccessedAt) {
    }

    /**
     * Trimmed notification row for the dashboard inbox preview.
     * Drops the {@code type}, {@code targetType}, {@code targetId},
     * and {@code readAt} fields surfaced on the full
     * {@link com.example.aichinese.dto.communication.NotificationResponse};
     * the dashboard widget only needs enough to render a
     * one-line preview with a click-through path.
     */
    public record NotificationSummary(
            Long notificationId,
            String title,
            String message,
            String status,
            String targetPath,
            LocalDateTime createdAt) {
    }
}
