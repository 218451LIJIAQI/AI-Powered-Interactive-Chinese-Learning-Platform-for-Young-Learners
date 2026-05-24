package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregate admin home dashboard payload returned by
 * {@code GET /api/v1/admin/dashboard}.
 *
 * <p>Drives the admin landing page on login. Designed as a
 * single-shot snapshot — every embedded summary is server-side
 * pre-computed so the front-end can hydrate the dashboard with
 * one round trip. The complementary
 * {@code AdminAnalyticsResponse} is the chart-heavy follow-up
 * for admins who want trend lines and breakdowns; this DTO is
 * deliberately scoped to "what needs my attention right now".</p>
 *
 * <h2>Headline counters</h2>
 * <p>{@link #totalStudents}, {@link #totalTeachers},
 * {@link #pendingTeacherApprovals}, {@link #totalModules},
 * {@link #totalLessons}, {@link #totalQuizzes},
 * {@link #totalGames} (the platform-managed activity library),
 * {@link #pendingRefunds}, {@link #pendingPayouts} — all primitive
 * {@code long} so the JSON wire format always emits numeric
 * values. The pending-* counts drive the "items needing action"
 * badges on the admin nav.</p>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #recentUserActivity} — most-recent user signups
 *       (newest first, server-capped) so the admin can spot
 *       suspicious registration spikes at a glance.</li>
 *   <li>{@link #aiUsageSummary} — running counts of AI requests
 *       and successful completions for ops monitoring.</li>
 *   <li>{@link #paymentRevenueSummary} — payment funnel summary
 *       plus lifetime revenue.</li>
 *   <li>{@link #recentAuditLogs} — most-recent audit-log entries
 *       (newest first, server-capped) — uses a trimmed
 *       {@link RecentAuditLogSummary} that omits PII fields like
 *       {@code ipAddress} / {@code requestId} surfaced on the
 *       full {@link com.example.aichinese.dto.communication.AuditLogResponse}.</li>
 * </ul>
 *
 * <p>{@link #fullName} is the admin's
 * {@code users.display_name} (historical naming carries through —
 * see the note on {@code TeacherStudentSummaryResponse#fullName})
 * used as the personalised greeting.</p>
 */
public record AdminDashboardResponse(
        String fullName,
        long totalStudents,
        long totalTeachers,
        long pendingTeacherApprovals,
        long totalModules,
        long totalLessons,
        long totalQuizzes,
        long totalGames,
        List<RecentUserActivity> recentUserActivity,
        AiUsageSummary aiUsageSummary,
        PaymentRevenueSummary paymentRevenueSummary,
        long pendingRefunds,
        long pendingPayouts,
        List<RecentAuditLogSummary> recentAuditLogs) {

    /**
     * One row of the "Recent signups" widget. {@link #fullName} is
     * {@code users.display_name}; {@link #roles} carries the
     * user's role names ({@code "STUDENT"}, {@code "TEACHER"},
     * {@code "ADMIN"}) so the dashboard can colour-code by role.
     */
    public record RecentUserActivity(
            Long userId,
            String fullName,
            String email,
            List<String> roles,
            LocalDateTime createdAt) {
    }

    /**
     * AI usage roll-up: total request count and successful subset.
     * Failure rate is computed client-side as
     * {@code 1 - successful/total}; surfacing both raw numbers
     * lets the dashboard show "X / Y AI requests succeeded today"
     * without recomputing on the server.
     */
    public record AiUsageSummary(
            long totalRequests,
            long successfulRequests) {
    }

    /**
     * Payment funnel summary: total transactions seen,
     * successfully-paid subset, and lifetime revenue.
     * {@link #totalRevenue} sums only PAID payments (refunds are
     * tracked separately on
     * {@link com.example.aichinese.dto.commerce.AdminRefundRequestResponse}).
     */
    public record PaymentRevenueSummary(
            long totalTransactions,
            long paidTransactions,
            BigDecimal totalRevenue) {
    }

    /**
     * Trimmed audit-log row for the dashboard widget. Compared to
     * the full
     * {@link com.example.aichinese.dto.communication.AuditLogResponse},
     * intentionally drops PII fields ({@code ipAddress},
     * {@code requestId}, {@code actorEmail}) since the dashboard
     * widget is just a "what's happening" feed, not a forensic
     * audit log — clicking through to the dedicated audit-log
     * page yields the full row.
     */
    public record RecentAuditLogSummary(
            Long auditLogId,
            String actionType,
            String targetType,
            String description,
            LocalDateTime createdAt) {
    }
}
