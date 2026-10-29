package com.example.aichinese.service.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.admin.AdminAnalyticsResponse;
import com.example.aichinese.dto.admin.AdminDashboardResponse;
import com.example.aichinese.dto.admin.AdminPlatformNumbers;
import com.example.aichinese.dto.admin.AdminPlatformNumbers.DayCount;
import com.example.aichinese.dto.classes.ReviewViews;
import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.ReviewContentType;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.projection.StatusCountProjection;
import com.example.aichinese.service.classes.ClassReviewService;
import com.example.aichinese.service.support.SupportService;

import jakarta.persistence.EntityManager;

/**
 * The admin dashboard and analytics. Queue items come from their owners' services (content review, AI reports,
 * support, teacher applications); the numbers are counted here.
 */
@Service
public class AdminOverviewService {

    /** The dashboard shows the first two items of each queue. */
    static final int QUEUE_PREVIEW = 2;
    static final int DASHBOARD_DAYS = 7;
    /** A student whose quiz average in the period is below this needs support. */
    static final int NEEDS_SUPPORT_BELOW_PERCENT = 60;

    private final TeacherApplicationService teacherApplicationService;
    private final ClassReviewService classReviewService;
    private final SupportService supportService;
    private final AiResponseReportRepository aiResponseReportRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final EntityManager entityManager;

    public AdminOverviewService(
            TeacherApplicationService teacherApplicationService,
            ClassReviewService classReviewService,
            SupportService supportService,
            AiResponseReportRepository aiResponseReportRepository,
            AiUsageLogRepository aiUsageLogRepository,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            EntityManager entityManager) {
        this.teacherApplicationService = teacherApplicationService;
        this.classReviewService = classReviewService;
        this.supportService = supportService;
        this.aiResponseReportRepository = aiResponseReportRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        ReviewViews.Queue reviewQueue = classReviewService.queue(null);
        Page<AiResponseReport> aiReports = aiResponseReportRepository.findByStatusInOrderByCreatedAtDescIdDesc(
                List.of(AiResponseReportStatus.PENDING_REVIEW), PageRequest.of(0, QUEUE_PREVIEW));
        Map<ContentStatus, Long> content = contentByStatus();
        return new AdminDashboardResponse(
                new AdminDashboardResponse.Queue<>(
                        teacherApplicationService.countWaiting(), teacherApplicationService.waiting(QUEUE_PREVIEW)),
                new AdminDashboardResponse.Queue<>(
                        reviewQueue.total(),
                        reviewQueue.items().stream()
                                .limit(QUEUE_PREVIEW)
                                .map(item -> new AdminDashboardResponse.ReviewItem(
                                        "CLASS".equals(item.type()) ? ReviewContentType.PACKAGE : ReviewContentType.LESSON,
                                        item.lessonId() == null ? item.classId() : item.lessonId(),
                                        item.title(), item.teacherName(), item.classTitle(), item.submittedAt()))
                                .toList()),
                new AdminDashboardResponse.Queue<>(
                        aiReports.getTotalElements(),
                        aiReports.getContent().stream()
                                .map(report -> new AdminDashboardResponse.AiReportItem(
                                        report.getId(),
                                        report.getReasonCategory().name(),
                                        report.getFeatureKey().name(),
                                        report.getStudentUser() == null ? null : report.getStudentUser().getDisplayName(),
                                        report.getCreatedAt()))
                                .toList()),
                new AdminDashboardResponse.Queue<>(supportService.countOpen(), supportService.oldestOpen(QUEUE_PREVIEW)),
                numbers(content),
                registrationsPerDay(LocalDate.now().minusDays(DASHBOARD_DAYS - 1L), LocalDate.now()));
    }

    /** {@code days} is 7 or 30 (anything else is treated as 7). */
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse analytics(int days) {
        int period = days == 30 ? 30 : DASHBOARD_DAYS;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period - 1L);
        List<DayCount> registrations = registrationsPerDay(from, to);
        Map<ContentStatus, Long> content = contentByStatus();
        return new AdminAnalyticsResponse(
                period,
                from,
                to,
                numbers(content),
                registrations.stream().mapToLong(DayCount::count).sum(),
                registrations,
                learning(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                content,
                aiRequestsByStatus());
    }

    private AdminPlatformNumbers numbers(Map<ContentStatus, Long> content) {
        long students = userRepository.countExistingByRoleName(RoleName.STUDENT);
        long teachers = userRepository.countExistingByRoleName(RoleName.TEACHER);
        long admins = userRepository.countExistingByRoleName(RoleName.ADMIN);
        Map<AiRequestStatus, Long> ai = aiRequestsByStatus();
        return new AdminPlatformNumbers(
                new AdminPlatformNumbers.UserCounts(students + teachers + admins, students, teachers, admins),
                content.getOrDefault(ContentStatus.PUBLISHED, 0L),
                content.values().stream().mapToLong(Long::longValue).sum(),
                ai.values().stream().mapToLong(Long::longValue).sum(),
                ai.getOrDefault(AiRequestStatus.SUCCESS, 0L),
                auditLogRepository.count());
    }

    /** One entry per day from {@code from} to {@code to} (inclusive), days without sign-ups included. */
    private List<DayCount> registrationsPerDay(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (LocalDateTime createdAt : userRepository.findCreatedAtBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            perDay.merge(createdAt.toLocalDate(), 1L, Long::sum);
        }
        List<DayCount> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(new DayCount(day, perDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    /** Classes and lessons per status; every status is present. */
    private Map<ContentStatus, Long> contentByStatus() {
        Map<ContentStatus, Long> counts = new EnumMap<>(ContentStatus.class);
        for (ContentStatus status : ContentStatus.values()) {
            counts.put(status, 0L);
        }
        List<StatusCountProjection> rows = new ArrayList<>(coursePackageRepository.groupByContentStatus());
        rows.addAll(lessonRepository.groupByContentStatus());
        for (StatusCountProjection row : rows) {
            ContentStatus.tryFrom(row.getStatus()).ifPresent(status -> counts.merge(status, row.getCount(), Long::sum));
        }
        return counts;
    }

    private Map<AiRequestStatus, Long> aiRequestsByStatus() {
        Map<AiRequestStatus, Long> counts = new EnumMap<>(AiRequestStatus.class);
        for (AiRequestStatus status : AiRequestStatus.values()) {
            counts.put(status, 0L);
        }
        for (StatusCountProjection row : aiUsageLogRepository.groupByAiStatus()) {
            if (row.getStatus() == null) {
                continue;
            }
            for (AiRequestStatus status : AiRequestStatus.values()) {
                if (status.name().equalsIgnoreCase(row.getStatus().strip())) {
                    counts.merge(status, row.getCount(), Long::sum);
                }
            }
        }
        return counts;
    }

    private AdminAnalyticsResponse.Learning learning(LocalDateTime start, LocalDateTime end) {
        Set<Long> active = new HashSet<>(entityManager.createQuery("""
                        select distinct p.student.id from LessonProgress p
                        where p.lastAccessedAt >= :start and p.lastAccessedAt < :end
                        """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList());
        List<Object[]> attempts = entityManager.createQuery("""
                        select a.student.id, a.score, a.maxScore from QuizAttempt a
                        where a.submitted = true and a.completedAt >= :start and a.completedAt < :end
                          and a.maxScore > 0 and a.score is not null
                        """, Object[].class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        Map<Long, double[]> perStudent = new HashMap<>();
        double percentSum = 0;
        for (Object[] attempt : attempts) {
            Long studentId = (Long) attempt[0];
            double percent = ((Integer) attempt[1]) * 100.0 / ((Integer) attempt[2]);
            percentSum += percent;
            active.add(studentId);
            perStudent.merge(studentId, new double[] { percent, 1 },
                    (left, right) -> new double[] { left[0] + right[0], left[1] + right[1] });
        }
        long needsSupport = perStudent.values().stream()
                .filter(total -> total[0] / total[1] < NEEDS_SUPPORT_BELOW_PERCENT)
                .count();
        long lessonsCompleted = entityManager.createQuery("""
                        select count(p) from LessonProgress p
                        where p.completed = true and p.updatedAt >= :start and p.updatedAt < :end
                        """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
        return new AdminAnalyticsResponse.Learning(
                active.size(),
                userRepository.countExistingByRoleName(RoleName.STUDENT),
                lessonsCompleted,
                attempts.isEmpty() ? null : (int) Math.round(percentSum / attempts.size()),
                needsSupport);
    }
}
