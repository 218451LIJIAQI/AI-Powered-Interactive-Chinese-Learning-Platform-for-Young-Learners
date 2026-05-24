package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Aggregate admin home dashboard payload returned by
 * {@code GET /api/v1/admin/dashboard}.
 *
 * <p>Drives the admin landing page on login. Designed as a
 * single-shot snapshot — every embedded summary is server-side
 * pre-computed so the front-end can hydrate the dashboard with
 * one round trip. The complementary
 * {@link com.example.aichinese.dto.analytics.AdminAnalyticsResponse}
 * is the chart-heavy follow-up for admins who want trend lines
 * and breakdowns; this DTO is deliberately scoped to "what needs
 * my attention right now".</p>
 *
 * <h2>Headline counters</h2>
 * <p>{@link #totalStudents}, {@link #totalTeachers},
 * {@link #pendingTeacherApprovals}, {@link #totalModules},
 * {@link #totalLessons}, {@link #totalQuizzes},
 * {@link #totalGames} (the platform-managed activity library),
 * {@link #pendingRefunds}, {@link #pendingPayouts} — all primitive
 * {@code long} so the JSON wire format always emits numeric
 * values. The {@code pending}-prefixed counts drive the "items
 * needing action" badges on the admin nav.</p>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #recentUserActivity} — most-recent user signups
 *       (newest first, server-capped to the top 5) so the admin
 *       can spot suspicious registration spikes at a glance.</li>
 *   <li>{@link #aiUsageSummary} — running counts of AI requests
 *       and successful completions for ops monitoring.</li>
 *   <li>{@link #paymentRevenueSummary} — payment-funnel summary
 *       plus lifetime revenue.</li>
 *   <li>{@link #recentAuditLogs} — most-recent audit-log entries
 *       (newest first, server-capped to the top 5). Uses the
 *       trimmed {@link RecentAuditLogSummary} that intentionally
 *       omits PII fields ({@code ipAddress}, {@code requestId},
 *       {@code actorEmail}) surfaced on the full
 *       {@link com.example.aichinese.dto.communication.AuditLogResponse}.</li>
 * </ul>
 *
 * <p>{@link #fullName} is the admin's {@code users.display_name}
 * (historical naming carries through — see the note on
 * {@code TeacherStudentSummaryResponse#fullName}) used as the
 * personalised greeting.</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>{@link #aiUsageSummary} and {@link #paymentRevenueSummary}
 *       are never {@code null}.</li>
 *   <li>{@link #recentUserActivity} and {@link #recentAuditLogs}
 *       are never {@code null} (empty list when no rows match) and
 *       are wrapped via
 *       {@link List#copyOf(java.util.Collection)} so the DTO is
 *       fully immutable and safe to share across requests.</li>
 *   <li>{@link #fullName} may be {@code null} when the admin's
 *       account has no display name set.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} on
 * the outer record and on every embedded inner record so upstream
 * Jackson upgrades cannot accidentally reshuffle the wire format
 * and break consumers that pin on field positions.</p>
 *
 * @param fullName                personalised greeting source
 *                                ({@code users.display_name});
 *                                may be {@code null}
 * @param totalStudents           count of users with the
 *                                {@code STUDENT} role
 * @param totalTeachers           count of users with the
 *                                {@code TEACHER} role
 * @param pendingTeacherApprovals teacher profiles awaiting admin
 *                                verification (badge source)
 * @param totalModules            total
 *                                {@link com.example.aichinese.entity.LearningModule}
 *                                rows
 * @param totalLessons            total
 *                                {@link com.example.aichinese.entity.Lesson}
 *                                rows
 * @param totalQuizzes            total
 *                                {@link com.example.aichinese.entity.Quiz}
 *                                rows
 * @param totalGames              total platform-managed game /
 *                                activity rows
 * @param recentUserActivity      most-recent user signups, newest
 *                                first; unmodifiable and never
 *                                {@code null}
 * @param aiUsageSummary          AI request roll-up; never
 *                                {@code null}
 * @param paymentRevenueSummary   payment-funnel summary; never
 *                                {@code null}
 * @param pendingRefunds          refund requests awaiting admin
 *                                decision (badge source)
 * @param pendingPayouts          teacher payout requests awaiting
 *                                admin decision (badge source)
 * @param recentAuditLogs         most-recent audit-log entries,
 *                                newest first; unmodifiable and
 *                                never {@code null}
 */
@JsonPropertyOrder({
        "fullName",
        "totalStudents",
        "totalTeachers",
        "pendingTeacherApprovals",
        "totalModules",
        "totalLessons",
        "totalQuizzes",
        "totalGames",
        "recentUserActivity",
        "aiUsageSummary",
        "paymentRevenueSummary",
        "pendingRefunds",
        "pendingPayouts",
        "recentAuditLogs"
})
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
     * Defensive canonical constructor: rejects {@code null}
     * non-list components and wraps every embedded list with
     * {@link List#copyOf(java.util.Collection) List.copyOf} so the
     * resulting DTO is fully immutable.
     */
    public AdminDashboardResponse {
        Objects.requireNonNull(aiUsageSummary, "aiUsageSummary must not be null");
        Objects.requireNonNull(paymentRevenueSummary,
                "paymentRevenueSummary must not be null");
        recentUserActivity = List.copyOf(
                Objects.requireNonNull(recentUserActivity,
                        "recentUserActivity must not be null"));
        recentAuditLogs = List.copyOf(
                Objects.requireNonNull(recentAuditLogs,
                        "recentAuditLogs must not be null"));
    }

    /**
     * One row of the "Recent signups" widget. {@link #fullName} is
     * {@code users.display_name}; {@link #roles} carries the
     * user's role names ({@code "STUDENT"}, {@code "TEACHER"},
     * {@code "ADMIN"}) so the dashboard can colour-code by role.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>{@link #userId}, {@link #email} and {@link #createdAt}
     *       are non-{@code null}; the {@code users} schema
     *       enforces them on the backing columns.</li>
     *   <li>{@link #fullName} may be {@code null} when the user
     *       has no display name set.</li>
     *   <li>{@link #roles} is never {@code null} (empty list when
     *       the user has no role assignments) and is wrapped via
     *       {@link List#copyOf(java.util.Collection)} so the row
     *       is fully immutable.</li>
     * </ul>
     *
     * @param userId    primary key of the
     *                  {@link com.example.aichinese.entity.User};
     *                  never {@code null}
     * @param fullName  the user's display name; may be {@code null}
     * @param email     the user's email address; never
     *                  {@code null}
     * @param roles     role names assigned to the user; unmodifiable
     *                  and never {@code null}
     * @param createdAt the user's signup timestamp; never
     *                  {@code null}
     */
    @JsonPropertyOrder({ "userId", "fullName", "email", "roles", "createdAt" })
    public record RecentUserActivity(
            Long userId,
            String fullName,
            String email,
            List<String> roles,
            LocalDateTime createdAt) {

        /**
         * Defensive canonical constructor: enforces non-null on
         * the persistence-guaranteed fields and wraps {@link #roles}
         * via {@link List#copyOf(java.util.Collection) List.copyOf}
         * so the row is fully immutable.
         */
        public RecentUserActivity {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(email, "email must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
        }
    }

    /**
     * AI usage roll-up: total request count and successful subset.
     * Failure rate is computed client-side as
     * {@code 1 - successfulRequests / totalRequests}; surfacing
     * both raw numbers lets the dashboard show "X / Y AI requests
     * succeeded today" without recomputing on the server.
     *
     * @param totalRequests       cumulative AI request count
     *                            (regardless of outcome)
     * @param successfulRequests  subset of {@link #totalRequests}
     *                            that completed successfully
     */
    @JsonPropertyOrder({ "totalRequests", "successfulRequests" })
    public record AiUsageSummary(
            long totalRequests,
            long successfulRequests) {
    }

    /**
     * Payment funnel summary: total transactions seen,
     * successfully-paid subset, and lifetime revenue.
     * {@link #totalRevenue} sums only {@code PAID} payments
     * (refunds are tracked separately on
     * {@link com.example.aichinese.dto.commerce.AdminRefundRequestResponse}).
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>{@link #totalRevenue} is never {@code null}; the
     *       canonical constructor substitutes
     *       {@link BigDecimal#ZERO} when the producer passes
     *       {@code null}.</li>
     * </ul>
     *
     * @param totalTransactions  count of every payment row,
     *                           including failed and refunded
     * @param paidTransactions   subset of {@link #totalTransactions}
     *                           in the {@code PAID} state
     * @param totalRevenue       sum of {@code amount} across all
     *                           {@code PAID} payments; never
     *                           {@code null} (defaults to
     *                           {@link BigDecimal#ZERO})
     */
    @JsonPropertyOrder({ "totalTransactions", "paidTransactions", "totalRevenue" })
    public record PaymentRevenueSummary(
            long totalTransactions,
            long paidTransactions,
            BigDecimal totalRevenue) {

        /**
         * Defensive canonical constructor: substitutes
         * {@link BigDecimal#ZERO} for a {@code null}
         * {@link #totalRevenue} so the frontend never has to
         * branch on a missing monetary aggregate.
         */
        public PaymentRevenueSummary {
            if (totalRevenue == null) {
                totalRevenue = BigDecimal.ZERO;
            }
        }
    }

    /**
     * Trimmed audit-log row for the dashboard widget. Compared to
     * the full
     * {@link com.example.aichinese.dto.communication.AuditLogResponse},
     * intentionally drops PII fields ({@code ipAddress},
     * {@code requestId}, {@code actorEmail}) since the dashboard
     * widget is just a "what's happening" feed, not a forensic
     * audit log — clicking through to the dedicated audit-log page
     * yields the full row.
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>{@link #auditLogId}, {@link #actionType} and
     *       {@link #createdAt} are never {@code null}; they map to
     *       the primary key, a {@code NOT NULL} column, and a
     *       managed timestamp respectively.</li>
     *   <li>{@link #targetType} and {@link #description} may be
     *       {@code null} for target-less actions or when the
     *       producer logged none.</li>
     * </ul>
     *
     * @param auditLogId  primary key of the underlying
     *                    {@link com.example.aichinese.entity.AuditLog};
     *                    never {@code null}
     * @param actionType  verb logged by the producer; never
     *                    {@code null}
     * @param targetType  entity type the action targeted; may be
     *                    {@code null}
     * @param description redacted human-readable description; may
     *                    be {@code null}
     * @param createdAt   timestamp the audit row was written;
     *                    never {@code null}
     */
    @JsonPropertyOrder({
            "auditLogId",
            "actionType",
            "targetType",
            "description",
            "createdAt"
    })
    public record RecentAuditLogSummary(
            Long auditLogId,
            String actionType,
            String targetType,
            String description,
            LocalDateTime createdAt) {

        /**
         * Defensive canonical constructor: enforces non-null on the
         * fields that the persistence layer unconditionally
         * guarantees.
         */
        public RecentAuditLogSummary {
            Objects.requireNonNull(auditLogId, "auditLogId must not be null");
            Objects.requireNonNull(actionType, "actionType must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }
}
