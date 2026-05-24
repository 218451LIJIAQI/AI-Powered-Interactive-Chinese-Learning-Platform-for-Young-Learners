package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Platform-wide analytics returned by
 * {@code GET /api/v1/admin/analytics}.
 *
 * <p>Drives the admin analytics dashboard — a single round-trip
 * payload that combines headline KPIs ({@link Overview}) with a
 * registration trend line and four categorical breakdowns. The
 * front-end binds each list directly to a chart without recomputing
 * counts.</p>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #userRegistrationTrend} — daily user signup trend
 *       over the recent window (typically 30 days). One
 *       {@link AnalyticsTrendPointResponse} per day with a date
 *       label and the count as {@link BigDecimal} (kept as the
 *       generic trend type).</li>
 *   <li>{@link #teacherApprovalBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.TeacherStatus}
 *       (PENDING / APPROVED / REJECTED).</li>
 *   <li>{@link #contentStatusBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.ContentStatus}
 *       across all owned-content tables (modules, lessons,
 *       packages combined).</li>
 *   <li>{@link #paymentStatusBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.PaymentStatus}.</li>
 *   <li>{@link #aiStatusBreakdown} — counts by AI request outcome
 *       (success / failure / rate-limited) for usage-monitoring.</li>
 * </ul>
 *
 * <h2>{@link Overview}</h2>
 * <p>Headline counters surfaced as the top "stat tiles" of the
 * admin dashboard. All counts are primitive {@code long} so the
 * JSON always emits numeric values; {@link Overview#totalRevenue()}
 * uses {@link BigDecimal} because it is a monetary aggregate.
 * {@link Overview#pendingTeacherApprovals},
 * {@link Overview#pendingRefunds}, and
 * {@link Overview#pendingPayouts} drive the "items needing
 * action" badges on the admin nav.</p>
 *
 * <p>Companion DTOs: {@link StudentAnalyticsResponse} and
 * {@link TeacherAnalyticsResponse} are the per-user analytics
 * endpoints with overlapping but role-scoped fields.</p>
 */
public record AdminAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> userRegistrationTrend,
        List<AnalyticsBreakdownItemResponse> teacherApprovalBreakdown,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> paymentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> aiStatusBreakdown) {

    /**
     * Headline KPI block rendered as the top stat tiles on the
     * admin analytics dashboard. All count fields are primitive
     * {@code long}; the single monetary field uses
     * {@link BigDecimal}.
     */
    public record Overview(
            long totalStudents,
            long totalTeachers,
            long pendingTeacherApprovals,
            long approvedTeachers,
            long rejectedTeachers,
            long publishedContentItems,
            BigDecimal totalRevenue,
            long pendingRefunds,
            long pendingPayouts,
            long moderationActions,
            long auditLogEntries,
            long adminUnreadNotifications,
            long aiRequests) {
    }
}
