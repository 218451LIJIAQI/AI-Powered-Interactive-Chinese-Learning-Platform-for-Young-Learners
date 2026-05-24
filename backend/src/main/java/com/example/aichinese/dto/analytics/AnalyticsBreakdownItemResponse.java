package com.example.aichinese.dto.analytics;

/**
 * Generic categorical breakdown row used across the analytics
 * endpoints to render pie / bar chart segments.
 *
 * <p>Embedded inside the {@code *Breakdown} list fields on
 * {@link AdminAnalyticsResponse}, {@link StudentAnalyticsResponse},
 * and {@link TeacherAnalyticsResponse}. The same record shape is
 * reused for every breakdown axis (teacher approval status,
 * content status, payment status, lesson status, weak areas, etc.)
 * because each is fundamentally a {@code (label, count)} pair.</p>
 *
 * <p>{@link #label} is the human-readable category name (e.g.
 * {@code "PUBLISHED"}, {@code "PENDING"}, {@code "PRONUNCIATION"})
 * — the front-end maps it to a localised display string.
 * {@link #value} is the row count (primitive {@code long} so the
 * JSON wire format always emits a numeric value, including
 * {@code 0} for empty buckets).</p>
 *
 * <p>For continuous time-series points use
 * {@link AnalyticsTrendPointResponse} (which carries
 * {@link java.math.BigDecimal} so it can represent percentages and
 * monetary trends as well as counts).</p>
 */
public record AnalyticsBreakdownItemResponse(
        String label,
        long value) {
}
