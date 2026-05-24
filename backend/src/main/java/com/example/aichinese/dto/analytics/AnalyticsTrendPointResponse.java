package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * are server-side ordered chronologically (oldest first) so the
 * front-end plots them as-is.</p>
 *
 * <p>{@link #label} is the bucket key formatted by
 * {@code AnalyticsService} — typically an ISO date string like
 * {@code "2026-05-23"} for daily trends or
 * {@code "2026-05"} for monthly earnings — that the front-end
 * formats for the chosen locale. {@link #value} is the metric
 * value at that bucket; using {@link BigDecimal} (vs {@code long}
 * on {@link AnalyticsBreakdownItemResponse}) lets the same record
 * shape carry counts, percentages (e.g. quiz-score average), and
 * monetary trends without per-metric DTO duplication.</p>
 *
 * <p>For categorical breakdowns use
 * {@link AnalyticsBreakdownItemResponse}.</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>{@link #label} is never {@code null}; every trend point
 *       must be plottable on the x-axis.</li>
 *   <li>{@link #value} is never {@code null}; the canonical
 *       constructor substitutes {@link BigDecimal#ZERO} when the
 *       producer passes {@code null} so the chart code never has
 *       to branch on a missing y-value.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break chart consumers that pin on field positions.</p>
 *
 * @param label bucket key formatted by the producer (typically an
 *              ISO date / year-month string); never {@code null}
 * @param value metric value at that bucket; never {@code null}
 *              (defaults to {@link BigDecimal#ZERO} when the
 *              producer passes {@code null})
 */
@JsonPropertyOrder({ "label", "value" })
public record AnalyticsTrendPointResponse(
        String label,
        BigDecimal value) {

    /**
     * Defensive canonical constructor: rejects {@code null} labels
     * and substitutes {@link BigDecimal#ZERO} for a {@code null}
     * {@link #value}, so the frontend never has to branch on a
     * missing y-value when rendering a trend line.
     */
    public AnalyticsTrendPointResponse {
        Objects.requireNonNull(label, "label must not be null");
        if (value == null) {
            value = BigDecimal.ZERO;
        }
    }
}
