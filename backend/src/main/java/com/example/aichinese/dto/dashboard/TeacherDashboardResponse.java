package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Aggregate teacher home dashboard payload returned by
 * {@code GET /api/v1/teachers/dashboard}.
 *
 * <p>Drives the teacher landing page on login — bundles the
 * teacher's catalogue footprint, student engagement, earnings
 * snapshot, and latest signals (quiz attempts on their content,
 * notifications) into one round trip. The complementary
 * {@code TeacherAnalyticsResponse} is the chart-heavy follow-up.</p>
 *
 * <h2>{@link #teacherStatus}</h2>
 * <p>The teacher's current verification state
 * ({@link TeacherStatus}). When not
 * {@link TeacherStatus#APPROVED}, the dashboard shows a
 * verification banner and most authoring actions are gated. The
 * status here is the canonical platform-side state and matches the
 * one on {@code TeacherProfileResponse} /
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
 *       {@link RecentQuizResult} adds {@link RecentQuizResult#studentName}
 *       (the
 *       {@link com.example.aichinese.entity.User#getDisplayName()})
 *       which the student-facing
 *       {@link StudentDashboardResponse.RecentQuizResult} does not
 *       carry — teachers care about who attempted, students do
 *       not.</li>
 *   <li>{@link #recentNotifications} — same shape as
 *       {@link StudentDashboardResponse.NotificationSummary}; the
 *       per-role inboxes feed it.</li>
 * </ul>
 *
 * <p>{@link #fullName} is the teacher's
 * {@code users.display_name} (historical naming carries through —
 * see {@code TeacherStudentSummaryResponse#fullName}).</p>
 */
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
     * Earnings snapshot mirroring the four totals on
     * {@code TeacherEarningSummaryResponse}. Surfaced inline here
     * so the home dashboard can show "Available: RM 320" without
     * hitting the dedicated earnings endpoint.
     */
    public record EarningsSummary(
            BigDecimal grossAmount,
            BigDecimal netAmount,
            BigDecimal availableBalance,
            BigDecimal pendingBalance) {
    }

    /**
     * Trimmed quiz attempt row for the teacher's "Recent attempts
     * on my quizzes" widget. Adds {@link #studentName} (which the
     * student-facing equivalent does not carry) so teachers can
     * see who attempted at a glance.
     */
    public record RecentQuizResult(
            Long attemptId,
            String quizTitle,
            String studentName,
            Integer score,
            Integer maxScore,
            LocalDateTime completedAt) {
    }

    /**
     * Trimmed notification row, identical in shape to
     * {@link StudentDashboardResponse.NotificationSummary}; both
     * roles share the same dashboard widget renderer.
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
