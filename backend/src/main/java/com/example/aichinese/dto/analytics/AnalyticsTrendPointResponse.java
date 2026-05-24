package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;

/**
 * One time-series point in an analytics trend chart.
 *
 * <p>Embedded inside the {@code *Trend} list fields on
 * {@link AdminAnalyticsResponse}
 * ({@code userRegistrationTrend}),
 * {@link StudentAnalyticsResponse}
 * ({@code quizScoreTrend}, {@code activityTrend}), and
 * {@link TeacherAnalyticsResponse}
 * ({@code quizScoreTrend}, {@code earningsTrend}). The trend lists
 * are server-side ordered chronologically so the front-end plots
 * them as-is.</p>
 *
 * <p>{@link #label} is the bucket key (typically a date string
 * like {@code "2026-05-23"} for daily trends, or
 * {@code "2026-W21"} for weekly) — the front-end formats it for
 * the chosen locale. {@link #value} is the metric value at that
 * bucket; using {@link BigDecimal} (vs {@code long} on
 * {@link AnalyticsBreakdownItemResponse}) lets the same record
 * shape carry counts, percentages (e.g. quiz score average), and
 * monetary trends without per-metric DTO duplication.</p>
 *
 * <p>For categorical breakdowns use
 * {@link AnalyticsBreakdownItemResponse}.</p>
 */
public record AnalyticsTrendPointResponse(
        String label,
        BigDecimal value) {
}
