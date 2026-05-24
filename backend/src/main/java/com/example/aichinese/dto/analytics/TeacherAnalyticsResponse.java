package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-teacher analytics returned by
 * {@code GET /api/v1/teachers/analytics} — the calling teacher's
 * own catalogue and earnings telemetry.
 *
 * <p>Drives the teacher-side "My analytics" page, complementing
 * the lighter {@code TeacherDashboardResponse} used by the home
 * dashboard. Mixes content-authoring metrics (lesson/module/package
 * counts, content status breakdown) with monetisation metrics
 * (earnings trend, balance) since teachers care about both as
 * authors and as earners.</p>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #quizScoreTrend} — daily average score across all
 *       attempts on the teacher's quizzes — i.e. how well the
 *       teacher's students are doing on their content.</li>
 *   <li>{@link #earningsTrend} — daily net earnings (after
 *       commission) over the recent window. {@link BigDecimal}
 *       values for monetary precision.</li>
 *   <li>{@link #contentStatusBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.ContentStatus}
 *       across all the teacher's owned content (modules + lessons
 *       + packages combined).</li>
 * </ul>
 *
 * <h2>{@link Overview}</h2>
 * <p>Headline counters split across three families:</p>
 * <ul>
 *   <li>Catalogue: {@link Overview#totalModules},
 *       {@link Overview#totalLessons}, {@link Overview#totalPackages},
 *       {@link Overview#publishedContentItems},
 *       {@link Overview#submittedContentItems} — the teacher's
 *       authoring footprint.</li>
 *   <li>Engagement: {@link Overview#totalActiveStudents},
 *       {@link Overview#averageQuizScore},
 *       {@link Overview#upcomingLiveClasses},
 *       {@link Overview#totalLiveEnrolments}.</li>
 *   <li>Earnings: {@link Overview#netEarnings},
 *       {@link Overview#availableBalance} — same numbers as on
 *       {@code TeacherEarningSummaryResponse}, surfaced here so
 *       the analytics page is self-contained.</li>
 * </ul>
 */
public record TeacherAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> earningsTrend,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown) {

    /**
     * Headline KPI block for the teacher analytics page; mixes
     * catalogue counts (primitive {@code long}), engagement
     * counts, and monetary aggregates ({@link BigDecimal}).
     */
    public record Overview(
            long totalModules,
            long totalLessons,
            long totalPackages,
            long publishedContentItems,
            long submittedContentItems,
            long totalActiveStudents,
            BigDecimal averageQuizScore,
            BigDecimal netEarnings,
            BigDecimal availableBalance,
            long upcomingLiveClasses,
            long totalLiveEnrolments) {
    }
}
