package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *       over the recent window (currently 30 days, sourced from
 *       {@code AnalyticsService.buildUserRegistrationTrend}). One
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
 *       (success / failure / rate-limited / blocked) for
 *       usage-monitoring.</li>
 * </ul>
 *
 * <h2>{@link Overview}</h2>
 * <p>Headline counters surfaced as the top "stat tiles" of the
 * admin dashboard. All count fields are primitive {@code long} so
 * the JSON always emits numeric values; {@link Overview#totalRevenue()}
 * uses {@link BigDecimal} because it is a monetary aggregate that
 * must not lose precision in transit.
 * {@link Overview#pendingTeacherApprovals()},
 * {@link Overview#pendingRefunds()}, and
 * {@link Overview#pendingPayouts()} drive the "items needing
 * action" badges on the admin nav.</p>
 *
 * <p>Companion DTOs: {@link StudentAnalyticsResponse} and
 * {@link TeacherAnalyticsResponse} are the per-user analytics
 * endpoints with overlapping but role-scoped fields.</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>{@link #overview} is never {@code null}.</li>
 *   <li>Each embedded list is never {@code null} (empty list when
 *       a backing query returned no rows) and is wrapped via
 *       {@link List#copyOf(java.util.Collection)} so the DTO is
 *       safe to share across requests / threads and external
 *       callers cannot mutate the payload after construction.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} on
 * both the outer record and the embedded {@link Overview} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param overview                  headline KPI block; never
 *                                  {@code null}
 * @param userRegistrationTrend     daily user-registration count
 *                                  trend, oldest day first;
 *                                  unmodifiable and never
 *                                  {@code null}
 * @param teacherApprovalBreakdown  count of teacher profiles by
 *                                  {@link com.example.aichinese.entity.enums.TeacherStatus};
 *                                  unmodifiable and never
 *                                  {@code null}
 * @param contentStatusBreakdown    aggregate count of all
 *                                  teacher-authored content rows by
 *                                  {@link com.example.aichinese.entity.enums.ContentStatus};
 *                                  unmodifiable and never
 *                                  {@code null}
 * @param paymentStatusBreakdown    count of payments by
 *                                  {@link com.example.aichinese.entity.enums.PaymentStatus};
 *                                  unmodifiable and never
 *                                  {@code null}
 * @param aiStatusBreakdown         count of AI requests by outcome
 *                                  (success / failure / rate-limited
 *                                  / blocked); unmodifiable and
 *                                  never {@code null}
 */
@JsonPropertyOrder({
        "overview",
        "userRegistrationTrend",
        "teacherApprovalBreakdown",
        "contentStatusBreakdown",
        "paymentStatusBreakdown",
        "aiStatusBreakdown"
})
public record AdminAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> userRegistrationTrend,
        List<AnalyticsBreakdownItemResponse> teacherApprovalBreakdown,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> paymentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> aiStatusBreakdown) {

    /**
     * Defensive canonical constructor: rejects {@code null}
     * components and wraps every embedded list with
     * {@link List#copyOf(java.util.Collection) List.copyOf} so the
     * resulting DTO is fully immutable.
     * {@link List#copyOf(java.util.Collection) List.copyOf} is
     * cheap when the input is already an unmodifiable list (the
     * typical {@code Stream.toList()} case in the analytics
     * service).
     */
    public AdminAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        userRegistrationTrend = List.copyOf(
                Objects.requireNonNull(userRegistrationTrend,
                        "userRegistrationTrend must not be null"));
        teacherApprovalBreakdown = List.copyOf(
                Objects.requireNonNull(teacherApprovalBreakdown,
                        "teacherApprovalBreakdown must not be null"));
        contentStatusBreakdown = List.copyOf(
                Objects.requireNonNull(contentStatusBreakdown,
                        "contentStatusBreakdown must not be null"));
        paymentStatusBreakdown = List.copyOf(
                Objects.requireNonNull(paymentStatusBreakdown,
                        "paymentStatusBreakdown must not be null"));
        aiStatusBreakdown = List.copyOf(
                Objects.requireNonNull(aiStatusBreakdown,
                        "aiStatusBreakdown must not be null"));
    }

    /**
     * Headline KPI block rendered as the top stat tiles on the
     * admin analytics dashboard. All count fields are primitive
     * {@code long}; the single monetary field uses
     * {@link BigDecimal} so JSON serialisation cannot lose
     * fractional currency precision.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>All count fields are primitive and therefore always
     *       emitted as numeric JSON values.</li>
     *   <li>{@link #totalRevenue} is never {@code null}; the
     *       canonical constructor substitutes
     *       {@link BigDecimal#ZERO} when the producer passes
     *       {@code null} so the frontend never has to branch on
     *       a null monetary aggregate.</li>
     * </ul>
     *
     * <h2>Wire format</h2>
     * <p>The component order is locked via {@link JsonPropertyOrder}.</p>
     *
     * @param totalStudents             count of users with the
     *                                  {@code STUDENT} role
     * @param totalTeachers             count of users with the
     *                                  {@code TEACHER} role
     * @param pendingTeacherApprovals   teacher profiles awaiting
     *                                  admin verification (badge
     *                                  source)
     * @param approvedTeachers          teacher profiles in the
     *                                  {@code APPROVED} state
     * @param rejectedTeachers          teacher profiles in the
     *                                  {@code REJECTED} state
     * @param publishedContentItems     count of teacher-authored
     *                                  rows currently in the
     *                                  {@code PUBLISHED} state
     *                                  across all content tables
     * @param totalRevenue              cumulative platform revenue
     *                                  in the base currency; never
     *                                  {@code null} (defaults to
     *                                  {@link BigDecimal#ZERO})
     * @param pendingRefunds            refund requests awaiting
     *                                  admin decision (badge source)
     * @param pendingPayouts            teacher payout requests
     *                                  awaiting admin decision
     *                                  (badge source)
     * @param moderationActions         total {@code content_moderation_records}
     *                                  rows persisted (audit trail
     *                                  size indicator)
     * @param auditLogEntries           total {@code audit_logs} rows
     *                                  persisted (audit trail size
     *                                  indicator)
     * @param adminUnreadNotifications  unread notifications for the
     *                                  calling admin (drives the
     *                                  bell-icon badge)
     * @param aiRequests                cumulative AI request count
     *                                  (usage-monitoring stat)
     */
    @JsonPropertyOrder({
            "totalStudents",
            "totalTeachers",
            "pendingTeacherApprovals",
            "approvedTeachers",
            "rejectedTeachers",
            "publishedContentItems",
            "totalRevenue",
            "pendingRefunds",
            "pendingPayouts",
            "moderationActions",
            "auditLogEntries",
            "adminUnreadNotifications",
            "aiRequests"
    })
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

        /**
         * Defensive canonical constructor: substitutes
         * {@link BigDecimal#ZERO} for a {@code null}
         * {@link #totalRevenue} so the frontend never has to branch
         * on a missing monetary aggregate. Negative count values
         * are not rejected here because some legitimate ledger
         * accounting paths produce signed deltas; aggregate counts
         * are nonetheless expected to be non-negative under normal
         * operation.
         */
        public Overview {
            if (totalRevenue == null) {
                totalRevenue = BigDecimal.ZERO;
            }
        }
    }
}
