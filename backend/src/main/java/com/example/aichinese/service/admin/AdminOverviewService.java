package com.example.aichinese.service.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.admin.AdminAnalyticsResponse;
import com.example.aichinese.dto.admin.AdminAnalyticsResponse.DayCount;
import com.example.aichinese.dto.admin.AdminDashboardResponse;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.support.SupportService;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

/**
 * The admin dashboard (each queue's count and first items; the support queue comes from {@link SupportService}) and
 * the analytics numbers.
 */
@Service
@RequiredArgsConstructor
public class AdminOverviewService {

    /** The dashboard shows the first two items of each queue. */
    private static final int QUEUE_PREVIEW = 2;
    /** A student whose quiz average in the period is below this needs support. */
    private static final int NEEDS_SUPPORT_BELOW_PERCENT = 60;
    private static final String SCORED_ATTEMPTS_IN_PERIOD = """
             from QuizAttempt a
             where a.submitted = true and a.completedAt >= :start and a.completedAt < :end
               and a.maxScore > 0 and a.score is not null and a.student.user.deletedAt is null
            """;

    private final SupportService supportService;
    private final AiResponseReportRepository aiResponseReportRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final LearnClock clock;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        Page<AdminDashboardResponse.AiReportItem> aiReports = aiResponseReportRepository.findDashboardItems(
                AiResponseReportStatus.PENDING_REVIEW, PageRequest.of(0, QUEUE_PREVIEW));
        return new AdminDashboardResponse(
                classDrafts(),
                new AdminDashboardResponse.Queue<>(aiReports.getTotalElements(), aiReports.getContent()),
                new AdminDashboardResponse.Queue<>(supportService.countOpen(), supportService.oldestOpen(QUEUE_PREVIEW)));
    }

    /** The "Class drafts" card: how many classes are drafts, and the newest ones with their lesson counts. */
    private AdminDashboardResponse.Queue<AdminDashboardResponse.ClassDraft> classDrafts() {
        Page<Long> draftIds = coursePackageRepository.findNewestDraftIds(PageRequest.of(0, QUEUE_PREVIEW));
        return new AdminDashboardResponse.Queue<>(draftIds.getTotalElements(),
                draftIds.isEmpty() ? List.of() : coursePackageRepository.summarizeDrafts(draftIds.getContent()));
    }

    /**
     * {@code days} is 7 or 30 (anything else is treated as 7). The days are platform days ({@link LearnClock}), the
     * same "today" as streaks and daily goals, whatever zone the server runs in.
     */
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse analytics(int days) {
        int period = days == 30 ? 30 : 7;
        LocalDate to = clock.today();
        LocalDate from = to.minusDays(period - 1L);
        LocalDateTime start = clock.startOf(from);
        LocalDateTime end = clock.startOf(to.plusDays(1));
        List<DayCount> registrations = registrationsPerDay(from, to, start, end);
        AdminAnalyticsResponse.UserCounts users = userRepository.countCurrentAccounts();
        Map<ContentStatus, Long> content = contentByStatus();
        Map<AiRequestStatus, Long> aiRequests = aiRequestsByStatus();
        return new AdminAnalyticsResponse(
                period,
                from,
                to,
                new AdminAnalyticsResponse.Numbers(
                        users,
                        content.get(ContentStatus.PUBLISHED),
                        aiRequests.values().stream().mapToLong(Long::longValue).sum()),
                registrations.stream().mapToLong(DayCount::count).sum(),
                registrations,
                learning(start, end, users.students()),
                content,
                aiRequests);
    }

    /**
     * One entry per platform day from {@code from} to {@code to} (inclusive), days without sign-ups included;
     * {@code start} and {@code end} are the server-local bounds of those days. Accounts deleted by their owners since
     * are left out, as in the user totals and the learning numbers.
     */
    private List<DayCount> registrationsPerDay(LocalDate from, LocalDate to, LocalDateTime start, LocalDateTime end) {
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (LocalDateTime createdAt : userRepository.findCreatedAtBetween(start, end)) {
            perDay.merge(clock.dayOf(createdAt), 1L, Long::sum);
        }
        return from.datesUntil(to.plusDays(1)).map(day -> new DayCount(day, perDay.getOrDefault(day, 0L))).toList();
    }

    /** Classes and lessons per status, each lesson once (a working copy is not a lesson); every status is present. */
    private Map<ContentStatus, Long> contentByStatus() {
        Map<ContentStatus, Long> counts = new EnumMap<>(ContentStatus.class);
        for (ContentStatus status : ContentStatus.values()) {
            counts.put(status, 0L);
        }
        Stream.concat(coursePackageRepository.groupByContentStatus().stream(),
                        lessonRepository.groupByContentStatus().stream())
                .forEach(row -> counts.merge(row.getStatus(), row.getCount(), Long::sum));
        return counts;
    }

    /** Every status is present; the column only holds these values (chk_ai_usage_logs_status). */
    private Map<AiRequestStatus, Long> aiRequestsByStatus() {
        Map<AiRequestStatus, Long> counts = new EnumMap<>(AiRequestStatus.class);
        for (AiRequestStatus status : AiRequestStatus.values()) {
            counts.put(status, 0L);
        }
        for (AiUsageLogRepository.FeatureStatusCount row : aiUsageLogRepository.countByFeatureAndStatus()) {
            counts.merge(row.status(), row.count(), Long::sum);
        }
        return counts;
    }

    /**
     * Students of existing (not deleted) accounts only, like {@code totalStudents}, so "active of total" cannot exceed
     * the total. A student is active after any learning in the period: a class lesson or quiz, a Learn path lesson or a
     * practice set. Lessons completed are class lessons and Learn path lessons (a bonus chest or checkpoint is not a
     * lesson), each counted in the period it was first completed: revisiting a completed lesson later does not count.
     */
    private AdminAnalyticsResponse.Learning learning(LocalDateTime start, LocalDateTime end, long totalStudents) {
        long active = entityManager.createQuery("""
                        select count(*) from (
                            select p.student.id as id from LessonProgress p
                            where p.lastAccessedAt >= :start and p.lastAccessedAt < :end and p.student.user.deletedAt is null
                            union
                            select c.student.id as id from LearnLessonCompletion c
                            where c.lastCompletedAt >= :start and c.lastCompletedAt < :end and c.student.user.deletedAt is null
                            union
                            select c.student.id as id from PracticeCompletion c
                            where c.createdAt >= :start and c.createdAt < :end and c.student.user.deletedAt is null
                            union
                            select a.student.id as id
                        """ + SCORED_ATTEMPTS_IN_PERIOD + ") activeStudents", Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
        long classLessonsCompleted = entityManager.createQuery("""
                        select count(p) from LessonProgress p
                        where p.completed = true and p.completedAt >= :start and p.completedAt < :end
                          and p.student.user.deletedAt is null
                        """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
        long pathLessonsCompleted = entityManager.createQuery("""
                        select count(c) from LearnLessonCompletion c
                        where c.firstCompletedAt >= :start and c.firstCompletedAt < :end
                          and c.lesson.lessonKind in :lessonKinds
                          and c.student.user.deletedAt is null
                        """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("lessonKinds", LearnLessonKind.COUNTED_AS_LESSONS)
                .getSingleResult();
        QuizStatistics scores = quizStatistics(start, end);
        Integer quizAverage = scores.average() == null ? null : (int) Math.round(scores.average());
        return new AdminAnalyticsResponse.Learning(active, totalStudents, classLessonsCompleted + pathLessonsCompleted,
                quizAverage, scores.needsSupport());
    }

    /** The average weights each attempt equally. Each learner below the threshold counts once. */
    private QuizStatistics quizStatistics(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
                        select sum(s.totalPercent) / sum(s.attempts),
                               count(case when s.totalPercent < :threshold * s.attempts then 1 end)
                        from (
                            select sum(100.0D * a.score / a.maxScore) as totalPercent, count(a) as attempts
                        """ + SCORED_ATTEMPTS_IN_PERIOD + " group by a.student.id) s", QuizStatistics.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("threshold", NEEDS_SUPPORT_BELOW_PERCENT)
                .getSingleResult();
    }

    record QuizStatistics(Double average, Long needsSupport) {
    }
}
