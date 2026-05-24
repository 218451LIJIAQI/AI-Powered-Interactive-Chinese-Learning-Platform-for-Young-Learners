package com.example.aichinese.dto.analytics;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Generic categorical breakdown row used across the analytics
 * endpoints to render pie / bar / stacked-bar chart segments.
 *
 * <p>Embedded inside the {@code *Breakdown} list fields on
 * {@link AdminAnalyticsResponse}, {@link StudentAnalyticsResponse},
 * and {@link TeacherAnalyticsResponse}. The same record shape is
 * reused for every breakdown axis (teacher approval status,
 * content status, payment status, lesson status, weak-area tags,
 * etc.) because each is fundamentally a {@code (label, count)}
 * pair.</p>
 *
 * <p>{@link #label} is the canonical category key — typically the
 * {@code .name()} of an enum such as
 * {@link com.example.aichinese.entity.enums.ContentStatus},
 * {@link com.example.aichinese.entity.enums.TeacherStatus},
 * {@link com.example.aichinese.entity.enums.PaymentStatus},
 * {@link com.example.aichinese.entity.enums.LessonStatus}, or
 * {@link com.example.aichinese.entity.enums.TeacherFeedbackType}
 * — that the front-end maps to a localised display string.
 * {@link #value} is the count for that category (primitive
 * {@code long} so the JSON wire format always emits a numeric
 * value, including {@code 0} for empty buckets when the producer
 * chose to include them).</p>
 *
 * <p>For continuous time-series points use
 * {@link AnalyticsTrendPointResponse}, which carries
 * {@link java.math.BigDecimal} so it can represent percentages and
 * monetary trends as well as counts.</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>{@link #label} is never {@code null} — every chart segment
 *       must be labellable on the legend.</li>
 *   <li>{@link #value} is always {@code >= 0}; the constructor
 *       rejects negative counts which would indicate a producer
 *       bug (a category count is a cardinal number).</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break chart consumers that pin on field positions.</p>
 *
 * @param label canonical category key (typically an enum
 *              {@code .name()}); never {@code null}
 * @param value count for this category; always {@code >= 0}
 */
@JsonPropertyOrder({ "label", "value" })
public record AnalyticsBreakdownItemResponse(
        String label,
        long value) {

    /**
     * Defensive canonical constructor: rejects {@code null} labels
     * and negative counts which would indicate a producer bug.
     */
    public AnalyticsBreakdownItemResponse {
        Objects.requireNonNull(label, "label must not be null");
        if (value < 0L) {
            throw new IllegalArgumentException(
                    "value must be non-negative but was " + value);
        }
    }
}
