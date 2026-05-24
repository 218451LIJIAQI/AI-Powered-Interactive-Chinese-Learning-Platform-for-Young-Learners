package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-teacher analytics returned by
 * {@code GET /api/v1/teachers/analytics} — the calling teacher's
 * own catalogue and earnings telemetry.
 *
 * <p>Drives the teacher-side "My analytics" page, complementing
 * the lighter {@code TeacherDashboardResponse} used by the home
 * dashboard. Mixes content-authoring metrics (lesson / module /
 * package counts, content-status breakdown) with monetisation
 * metrics (earnings trend, balance) since teachers care about both
 * as authors and as earners.</p>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #quizScoreTrend} — daily average percentage score
 *       across all attempts on the teacher's quizzes (oldest first)
 *       — i.e. how well the teacher's students are doing on their
 *       content.</li>
 *   <li>{@link #earningsTrend} — monthly net earnings (after
 *       commission) over the recent window. Monetary
 *       {@link BigDecimal} values for currency-precision.</li>
 *   <li>{@link #contentStatusBreakdown} — counts by
 *       {@link com.example.aichinese.entity.enums.ContentStatus}
 *       across all the teacher's owned content (modules + lessons
 *       + packages combined).</li>
 * </ul>
 *
 * <h2>{@link Overview}</h2>
 * <p>Headline counters split across three families:</p>
 * <ul>
 *   <li><b>Catalogue:</b> {@link Overview#totalModules()},
 *       {@link Overview#totalLessons()},
 *       {@link Overview#totalPackages()},
 *       {@link Overview#publishedContentItems()},
 *       {@link Overview#submittedContentItems()} — the teacher's
 *       authoring footprint.</li>
 *   <li><b>Engagement:</b> {@link Overview#totalActiveStudents()},
 *       {@link Overview#averageQuizScore()},
 *       {@link Overview#upcomingLiveClasses()},
 *       {@link Overview#totalLiveEnrolments()}.</li>
 *   <li><b>Earnings:</b> {@link Overview#netEarnings()},
 *       {@link Overview#availableBalance()} — same numbers as on
 *       {@code TeacherEarningSummaryResponse}, surfaced here so
 *       the analytics page is self-contained without a follow-up
 *       lookup.</li>
 * </ul>
 *
 * <p>Companion DTOs: {@link AdminAnalyticsResponse} (platform
 * roll-up) and {@link StudentAnalyticsResponse} (per-student
 * roll-up).</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ul>
 *   <li>{@link #overview} is never {@code null}.</li>
 *   <li>Each embedded list is never {@code null} (empty list when
 *       a backing query returned no rows) and is wrapped via
 *       {@link List#copyOf(java.util.Collection)} so the DTO is
 *       fully immutable and safe to share across requests.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} on
 * both the outer record and the embedded {@link Overview} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param overview                headline KPI block; never
 *                                {@code null}
 * @param quizScoreTrend          daily average quiz-score
 *                                percentage across the teacher's
 *                                quizzes, oldest first;
 *                                unmodifiable and never
 *                                {@code null}
 * @param earningsTrend           monthly net-earnings trend,
 *                                oldest first; unmodifiable and
 *                                never {@code null}
 * @param contentStatusBreakdown  count of teacher-authored content
 *                                rows by
 *                                {@link com.example.aichinese.entity.enums.ContentStatus};
 *                                unmodifiable and never
 *                                {@code null}
 */
@JsonPropertyOrder({
        "overview",
        "quizScoreTrend",
        "earningsTrend",
        "contentStatusBreakdown"
})
public record TeacherAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> earningsTrend,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown) {

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
    public TeacherAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        quizScoreTrend = List.copyOf(
                Objects.requireNonNull(quizScoreTrend, "quizScoreTrend must not be null"));
        earningsTrend = List.copyOf(
                Objects.requireNonNull(earningsTrend, "earningsTrend must not be null"));
        contentStatusBreakdown = List.copyOf(
                Objects.requireNonNull(contentStatusBreakdown,
                        "contentStatusBreakdown must not be null"));
    }

    /**
     * Headline KPI block for the teacher analytics page; mixes
     * catalogue counts (primitive {@code long}), engagement
     * counts, and monetary aggregates ({@link BigDecimal}).
     *
     * <h2>Invariants</h2>
     * <ul>
     *   <li>All count fields are primitive and therefore always
     *       emitted as numeric JSON values.</li>
     *   <li>{@link #averageQuizScore}, {@link #netEarnings} and
     *       {@link #availableBalance} are never {@code null}; the
     *       canonical constructor substitutes
     *       {@link BigDecimal#ZERO} when the producer passes
     *       {@code null} so the frontend never has to branch on a
     *       missing aggregate (typical for a brand-new teacher
     *       with no quiz attempts or earnings yet).</li>
     * </ul>
     *
     * <h2>Wire format</h2>
     * <p>The component order is locked via {@link JsonPropertyOrder}.</p>
     *
     * @param totalModules            count of
     *                                {@link com.example.aichinese.entity.LearningModule}
     *                                rows the teacher owns
     * @param totalLessons            count of
     *                                {@link com.example.aichinese.entity.Lesson}
     *                                rows the teacher owns
     * @param totalPackages           count of
     *                                {@link com.example.aichinese.entity.CoursePackage}
     *                                rows the teacher owns
     * @param publishedContentItems   count of teacher-owned content
     *                                rows currently in the
     *                                {@code PUBLISHED} state
     * @param submittedContentItems   count of teacher-owned content
     *                                rows currently in the
     *                                {@code SUBMITTED} (awaiting
     *                                review) state
     * @param totalActiveStudents     unique students with at least
     *                                one tracked touch-point on the
     *                                teacher's content
     * @param averageQuizScore        0-100 percentage average across
     *                                attempts on the teacher's
     *                                quizzes; never {@code null}
     *                                (defaults to
     *                                {@link BigDecimal#ZERO})
     * @param netEarnings             cumulative net earnings (after
     *                                commission) in the platform
     *                                base currency; never
     *                                {@code null} (defaults to
     *                                {@link BigDecimal#ZERO})
     * @param availableBalance        currently withdrawable balance;
     *                                never {@code null} (defaults
     *                                to {@link BigDecimal#ZERO})
     * @param upcomingLiveClasses     count of live classes scheduled
     *                                in the future the teacher will
     *                                host
     * @param totalLiveEnrolments     accessible live-class enrolments
     *                                across the teacher's catalogue
     */
    @JsonPropertyOrder({
            "totalModules",
            "totalLessons",
            "totalPackages",
            "publishedContentItems",
            "submittedContentItems",
            "totalActiveStudents",
            "averageQuizScore",
            "netEarnings",
            "availableBalance",
            "upcomingLiveClasses",
            "totalLiveEnrolments"
    })
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

        /**
         * Defensive canonical constructor: substitutes
         * {@link BigDecimal#ZERO} for any {@code null}
         * monetary-or-percentage aggregate so the frontend never
         * has to branch on a missing numeric value when rendering
         * stat tiles.
         */
        public Overview {
            if (averageQuizScore == null) {
                averageQuizScore = BigDecimal.ZERO;
            }
            if (netEarnings == null) {
                netEarnings = BigDecimal.ZERO;
            }
            if (availableBalance == null) {
                availableBalance = BigDecimal.ZERO;
            }
        }
    }
}
