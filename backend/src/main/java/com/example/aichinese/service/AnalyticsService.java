package com.example.aichinese.service;

import static java.math.BigDecimal.ZERO;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.analytics.AdminAnalyticsResponse;
import com.example.aichinese.dto.analytics.AnalyticsBreakdownItemResponse;
import com.example.aichinese.dto.analytics.AnalyticsTrendPointResponse;
import com.example.aichinese.dto.analytics.StudentAnalyticsResponse;
import com.example.aichinese.dto.analytics.TeacherAnalyticsResponse;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.ContentModerationRecord;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.Report;
import com.example.aichinese.entity.Review;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.StudentProgress;
import com.example.aichinese.entity.SupportTicket;
import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.entity.enums.RefundStatus;
import com.example.aichinese.entity.enums.ReportStatus;
import com.example.aichinese.entity.enums.ReviewStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportTicketStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.ContentModerationRecordRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.RefundRequestRepository;
import com.example.aichinese.repository.ReportRepository;
import com.example.aichinese.repository.ReviewRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.StudentProgressRepository;
import com.example.aichinese.repository.SupportTicketRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class AnalyticsService {

    private static final int MAX_TREND_POINTS = 6;
    private static final int RECENT_ACTIVITY_DAYS = 7;
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final NotificationRepository notificationRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final TeacherEarningRepository teacherEarningRepository;
    private final LiveClassRepository liveClassRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final ReportRepository reportRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final ContentModerationRecordRepository contentModerationRecordRepository;
    private final AuditLogRepository auditLogRepository;
    private final AiUsageLogRepository aiUsageLogRepository;

    public AnalyticsService(
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            StudentProgressRepository studentProgressRepository,
            LessonProgressRepository lessonProgressRepository,
            QuizAttemptRepository quizAttemptRepository,
            VideoProgressRepository videoProgressRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            NotificationRepository notificationRepository,
            LearningModuleRepository learningModuleRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            TeacherEarningRepository teacherEarningRepository,
            LiveClassRepository liveClassRepository,
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            PaymentRecordRepository paymentRecordRepository,
            RefundRequestRepository refundRequestRepository,
            PayoutRequestRepository payoutRequestRepository,
            ReportRepository reportRepository,
            SupportTicketRepository supportTicketRepository,
            ContentModerationRecordRepository contentModerationRecordRepository,
            AuditLogRepository auditLogRepository,
            AiUsageLogRepository aiUsageLogRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.videoProgressRepository = videoProgressRepository;
        this.liveClassEnrolmentRepository = liveClassEnrolmentRepository;
        this.notificationRepository = notificationRepository;
        this.learningModuleRepository = learningModuleRepository;
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.teacherEarningRepository = teacherEarningRepository;
        this.liveClassRepository = liveClassRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.payoutRequestRepository = payoutRequestRepository;
        this.reportRepository = reportRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.contentModerationRecordRepository = contentModerationRecordRepository;
        this.auditLogRepository = auditLogRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
    }

    @Transactional(readOnly = true)
    public StudentAnalyticsResponse getStudentAnalytics(SecurityUserDetails currentUser) {
        StudentProfile student = studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));

        List<QuizAttempt> attempts = quizAttemptRepository.findByStudentIdOrderByCompletedAtDescCreatedAtDesc(student.getId());
        List<LessonProgress> lessonProgressList = lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId());
        List<VideoProgress> videoProgressList = videoProgressRepository.findByStudentIdOrderByLastWatchedAtDesc(student.getId());
        List<LiveClassEnrolment> enrolments = liveClassEnrolmentRepository
                .findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(student.getId(), LiveClassEnrolmentStatus.ENROLLED);
        StudentProgress latestProgress = studentProgressRepository.findFirstByStudentIdOrderByUpdatedAtDesc(student.getId())
                .orElse(null);

        long completedLessons = lessonProgressRepository.countByStudentIdAndCompletedTrue(student.getId());
        long totalTrackedLessons = lessonProgressRepository.countByStudentId(student.getId());
        long inProgressLessons = Math.max(totalTrackedLessons - completedLessons, 0);

        return new StudentAnalyticsResponse(
                new StudentAnalyticsResponse.Overview(
                        completedLessons,
                        inProgressLessons,
                        totalTrackedLessons,
                        scaleDecimal(quizAttemptRepository.findAveragePercentageByStudentId(student.getId())),
                        videoProgressList.stream().filter(VideoProgress::isCompleted).count(),
                        countUpcomingStudentLiveClasses(enrolments),
                        notificationRepository.countByUserIdAndNotificationStatus(
                                currentUser.getUserId(),
                                NotificationStatus.UNREAD)),
                buildQuizTrend(attempts),
                buildStudentActivityTrend(lessonProgressList, attempts, videoProgressList),
                buildStudentLessonStatusBreakdown(lessonProgressList),
                buildWeakAreaBreakdown(latestProgress),
                buildStudentLearningModeBreakdown(lessonProgressList));
    }

    @Transactional(readOnly = true)
    public TeacherAnalyticsResponse getTeacherAnalytics(SecurityUserDetails currentUser) {
        TeacherProfile teacher = teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));

        List<LearningModule> modules = learningModuleRepository.findByTeacherUserIdOrderByUpdatedAtDesc(currentUser.getUserId());
        List<Lesson> lessons = lessonRepository.findByTeacherUserIdOrderByUpdatedAtDesc(currentUser.getUserId());
        List<CoursePackage> packages = coursePackageRepository.findByTeacherUserIdOrderByUpdatedAtDesc(currentUser.getUserId());
        List<QuizAttempt> attempts = quizAttemptRepository.findByQuizTeacherIdOrderByCompletedAtDescCreatedAtDesc(teacher.getId());
        List<TeacherEarning> earnings = teacherEarningRepository.findByTeacherUserIdOrderByCreatedAtDesc(currentUser.getUserId());
        List<LiveClass> liveClasses = liveClassRepository.findByTeacherUserIdOrderByScheduledStartAtAsc(currentUser.getUserId());
        List<LiveClassEnrolment> liveEnrolments = liveClassEnrolmentRepository.findAll();
        List<Review> ownedReviews = reviewRepository.findAll().stream()
                .filter(review -> belongsToTeacher(review, currentUser.getUserId()))
                .toList();
        List<AiUsageLog> teacherAiLogs = aiUsageLogRepository.findAll().stream()
                .filter(log -> log.getUser() != null && log.getUser().getId().equals(currentUser.getUserId()))
                .toList();

        return new TeacherAnalyticsResponse(
                new TeacherAnalyticsResponse.Overview(
                        modules.size(),
                        lessons.size(),
                        packages.size(),
                        countByStatus(modules, lessons, packages, ContentStatus.PUBLISHED),
                        countByStatus(modules, lessons, packages, ContentStatus.SUBMITTED),
                        lessonProgressRepository.countDistinctStudentsByTeacherId(teacher.getId()),
                        scaleDecimal(quizAttemptRepository.findAveragePercentageByTeacherId(teacher.getId())),
                        averageReviewRating(ownedReviews, teacher.getAverageRating()),
                        defaultMoney(teacherEarningRepository.sumNetAmountByTeacherId(teacher.getId())),
                        defaultMoney(teacherEarningRepository.sumAvailableNetAmountByTeacherId(teacher.getId())),
                        countUpcomingTeacherLiveClasses(liveClasses),
                        countTeacherLiveEnrolments(liveEnrolments, teacher.getId()),
                        teacherAiLogs.size(),
                        teacherAiLogs.stream().filter(this::isSuccessfulAiLog).count()),
                buildTeacherQuizTrend(attempts),
                buildTeacherEarningsTrend(earnings),
                buildTeacherContentStatusBreakdown(modules, lessons, packages),
                buildTeacherReviewRatingBreakdown(ownedReviews));
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAdminAnalytics(SecurityUserDetails currentUser) {
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        List<Report> reports = reportRepository.findAll();
        List<Review> reviews = reviewRepository.findAll();
        List<SupportTicket> tickets = supportTicketRepository.findAll();
        List<ContentModerationRecord> moderationRecords = contentModerationRecordRepository.findAll();
        List<AiUsageLog> aiUsageLogs = aiUsageLogRepository.findAll();
        List<LearningModule> modules = learningModuleRepository.findAll();
        List<Lesson> lessons = lessonRepository.findAll();
        List<CoursePackage> packages = coursePackageRepository.findAll();
        List<PaymentRecord> payments = paymentRecordRepository.findAll();

        long openSupportTickets = tickets.stream()
                .filter(ticket -> ticket.getTicketStatus() == SupportTicketStatus.OPEN
                        || ticket.getTicketStatus() == SupportTicketStatus.IN_PROGRESS)
                .count();

        return new AdminAnalyticsResponse(
                new AdminAnalyticsResponse.Overview(
                        userRepository.countByRoleName(RoleName.STUDENT),
                        userRepository.countByRoleName(RoleName.TEACHER),
                        teacherProfileRepository.countByTeacherStatus(TeacherStatus.PENDING),
                        teacherProfileRepository.countByTeacherStatus(TeacherStatus.APPROVED),
                        teacherProfileRepository.countByTeacherStatus(TeacherStatus.REJECTED),
                        learningModuleRepository.countByContentStatus(ContentStatus.PUBLISHED)
                                + lessonRepository.countByContentStatus(ContentStatus.PUBLISHED)
                                + coursePackageRepository.countByContentStatus(ContentStatus.PUBLISHED),
                        defaultMoney(paymentRecordRepository.sumAmountByPaymentStatus(PaymentStatus.PAID)),
                        refundRequestRepository.countByRefundStatus(RefundStatus.PENDING),
                        payoutRequestRepository.countByPayoutStatus(PayoutStatus.PENDING),
                        openSupportTickets,
                        reports.stream().filter(report -> report.getReportStatus() == ReportStatus.PENDING).count(),
                        moderationRecords.size(),
                        auditLogRepository.count(),
                        notificationRepository.countByUserIdAndNotificationStatus(
                                currentUser.getUserId(),
                                NotificationStatus.UNREAD),
                        aiUsageLogs.size()),
                buildUserRegistrationTrend(users),
                buildTeacherApprovalBreakdown(),
                buildAdminContentStatusBreakdown(modules, lessons, packages),
                buildPaymentStatusBreakdown(payments),
                buildReportStatusBreakdown(reports),
                buildReviewStatusBreakdown(reviews),
                buildSupportTicketStatusBreakdown(tickets),
                buildAiStatusBreakdown(aiUsageLogs));
    }

    private List<AnalyticsTrendPointResponse> buildQuizTrend(List<QuizAttempt> attempts) {
        return attempts.stream()
                .filter(this::hasValidPercentage)
                .sorted(Comparator.comparing(this::resolveAttemptTime))
                .skip(Math.max(0, attempts.stream().filter(this::hasValidPercentage).count() - MAX_TREND_POINTS))
                .map(attempt -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(resolveAttemptTime(attempt).toLocalDate()),
                        percentageForAttempt(attempt)))
                .toList();
    }

    private List<AnalyticsTrendPointResponse> buildStudentActivityTrend(
            List<LessonProgress> lessonProgressList,
            List<QuizAttempt> attempts,
            List<VideoProgress> videoProgressList) {
        Map<LocalDate, BigDecimal> countsByDay = initializeRecentDayMap();

        lessonProgressList.forEach(progress -> addTrendPoint(countsByDay, resolveLessonProgressTime(progress), BigDecimal.ONE));
        attempts.forEach(attempt -> addTrendPoint(countsByDay, resolveAttemptTime(attempt), BigDecimal.ONE));
        videoProgressList.forEach(progress -> addTrendPoint(countsByDay, resolveVideoProgressTime(progress), BigDecimal.ONE));

        return countsByDay.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private List<AnalyticsBreakdownItemResponse> buildStudentLessonStatusBreakdown(List<LessonProgress> lessonProgressList) {
        long completed = lessonProgressList.stream().filter(LessonProgress::isCompleted).count();
        long inProgress = lessonProgressList.stream().filter(progress -> !progress.isCompleted()).count();
        return buildBreakdownFromMap(Map.of(
                "COMPLETED", completed,
                "IN_PROGRESS", inProgress));
    }

    private List<AnalyticsBreakdownItemResponse> buildWeakAreaBreakdown(StudentProgress latestProgress) {
        if (latestProgress == null || latestProgress.getWeakAreas() == null || latestProgress.getWeakAreas().isBlank()) {
            return List.of();
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String value : latestProgress.getWeakAreas().split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                counts.merge(trimmed, 1L, Long::sum);
            }
        }
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildStudentLearningModeBreakdown(List<LessonProgress> lessonProgressList) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LessonProgress progress : lessonProgressList) {
            Lesson lesson = progress.getLesson();
            if (lesson == null || lesson.getLearningType() == null) {
                continue;
            }
            counts.merge(lesson.getLearningType().name(), 1L, Long::sum);
        }
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsTrendPointResponse> buildTeacherQuizTrend(List<QuizAttempt> attempts) {
        if (attempts.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, List<BigDecimal>> grouped = new LinkedHashMap<>();
        attempts.stream()
                .filter(this::hasValidPercentage)
                .sorted(Comparator.comparing(this::resolveAttemptTime))
                .forEach(attempt -> grouped
                        .computeIfAbsent(resolveAttemptTime(attempt).toLocalDate(), ignored -> new ArrayList<>())
                        .add(percentageForAttempt(attempt)));

        return takeLastEntries(grouped, MAX_TREND_POINTS).stream()
                .map(entry -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(entry.getKey()),
                        average(entry.getValue())))
                .toList();
    }

    private List<AnalyticsTrendPointResponse> buildTeacherEarningsTrend(List<TeacherEarning> earnings) {
        if (earnings.isEmpty()) {
            return List.of();
        }

        Map<YearMonth, BigDecimal> grouped = new LinkedHashMap<>();
        earnings.stream()
                .sorted(Comparator.comparing(TeacherEarning::getCreatedAt))
                .forEach(earning -> grouped.merge(
                        YearMonth.from(earning.getCreatedAt()),
                        defaultMoney(earning.getNetAmount()),
                        BigDecimal::add));

        return takeLastEntries(grouped, MAX_TREND_POINTS).stream()
                .map(entry -> new AnalyticsTrendPointResponse(
                        MONTH_LABEL_FORMATTER.format(entry.getKey().atDay(1)),
                        entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private List<AnalyticsBreakdownItemResponse> buildTeacherContentStatusBreakdown(
            List<LearningModule> modules,
            List<Lesson> lessons,
            List<CoursePackage> packages) {
        Map<String, Long> counts = initializeContentStatusCounts();
        modules.forEach(module -> incrementContentStatus(counts, module.getContentStatus()));
        lessons.forEach(lesson -> incrementContentStatus(counts, lesson.getContentStatus()));
        packages.forEach(coursePackage -> incrementContentStatus(counts, coursePackage.getContentStatus()));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildTeacherReviewRatingBreakdown(List<Review> reviews) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Review review : reviews) {
            if (review.getRating() == null) {
                continue;
            }
            int bucket = review.getRating().setScale(0, RoundingMode.HALF_UP).intValue();
            counts.merge(String.valueOf(bucket), 1L, Long::sum);
        }
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsTrendPointResponse> buildUserRegistrationTrend(List<User> users) {
        Map<LocalDate, BigDecimal> countsByDay = initializeRecentDayMap();
        users.forEach(user -> addTrendPoint(countsByDay, user.getCreatedAt(), BigDecimal.ONE));

        return countsByDay.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private List<AnalyticsBreakdownItemResponse> buildTeacherApprovalBreakdown() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PENDING", teacherProfileRepository.countByTeacherStatus(TeacherStatus.PENDING));
        counts.put("APPROVED", teacherProfileRepository.countByTeacherStatus(TeacherStatus.APPROVED));
        counts.put("REJECTED", teacherProfileRepository.countByTeacherStatus(TeacherStatus.REJECTED));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildAdminContentStatusBreakdown(
            List<LearningModule> modules,
            List<Lesson> lessons,
            List<CoursePackage> packages) {
        Map<String, Long> counts = initializeContentStatusCounts();
        modules.forEach(module -> incrementContentStatus(counts, module.getContentStatus()));
        lessons.forEach(lesson -> incrementContentStatus(counts, lesson.getContentStatus()));
        packages.forEach(coursePackage -> incrementContentStatus(counts, coursePackage.getContentStatus()));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildPaymentStatusBreakdown(List<PaymentRecord> payments) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            counts.put(status.name(), 0L);
        }
        payments.forEach(payment -> counts.merge(payment.getPaymentStatus().name(), 1L, Long::sum));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildReportStatusBreakdown(List<Report> reports) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReportStatus status : ReportStatus.values()) {
            counts.put(status.name(), 0L);
        }
        reports.forEach(report -> counts.merge(report.getReportStatus().name(), 1L, Long::sum));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildReviewStatusBreakdown(List<Review> reviews) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReviewStatus status : ReviewStatus.values()) {
            counts.put(status.name(), 0L);
        }
        reviews.forEach(review -> counts.merge(review.getReviewStatus().name(), 1L, Long::sum));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildSupportTicketStatusBreakdown(List<SupportTicket> tickets) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SupportTicketStatus status : SupportTicketStatus.values()) {
            counts.put(status.name(), 0L);
        }
        tickets.forEach(ticket -> counts.merge(ticket.getTicketStatus().name(), 1L, Long::sum));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildAiStatusBreakdown(List<AiUsageLog> aiUsageLogs) {
        Map<String, Long> counts = new LinkedHashMap<>();
        aiUsageLogs.forEach(log -> counts.merge(normalizeStatus(log.getStatus()), 1L, Long::sum));
        return buildBreakdownFromMap(counts);
    }

    private boolean belongsToTeacher(Review review, Long teacherUserId) {
        if (review.getTargetType() == null || review.getTargetId() == null) {
            return false;
        }

        String targetType = review.getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("LESSON".equals(targetType)) {
            return lessonRepository.findById(review.getTargetId())
                    .map(Lesson::getTeacher)
                    .map(TeacherProfile::getUser)
                    .map(User::getId)
                    .filter(teacherUserId::equals)
                    .isPresent();
        }

        if ("PACKAGE".equals(targetType)) {
            return coursePackageRepository.findById(review.getTargetId())
                    .map(CoursePackage::getTeacher)
                    .map(TeacherProfile::getUser)
                    .map(User::getId)
                    .filter(teacherUserId::equals)
                    .isPresent();
        }

        return false;
    }

    private long countByStatus(
            List<LearningModule> modules,
            List<Lesson> lessons,
            List<CoursePackage> packages,
            ContentStatus targetStatus) {
        return modules.stream().filter(module -> module.getContentStatus() == targetStatus).count()
                + lessons.stream().filter(lesson -> lesson.getContentStatus() == targetStatus).count()
                + packages.stream().filter(coursePackage -> coursePackage.getContentStatus() == targetStatus).count();
    }

    private BigDecimal averageReviewRating(List<Review> reviews, BigDecimal fallbackAverage) {
        List<BigDecimal> ratings = reviews.stream()
                .map(Review::getRating)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (ratings.isEmpty()) {
            return fallbackAverage == null ? ZERO : fallbackAverage;
        }
        return average(ratings);
    }

    private long countUpcomingStudentLiveClasses(List<LiveClassEnrolment> enrolments) {
        LocalDateTime now = LocalDateTime.now();
        return enrolments.stream()
                .map(LiveClassEnrolment::getLiveClass)
                .filter(liveClass -> liveClass != null
                        && liveClass.getScheduledStartAt() != null
                        && liveClass.getScheduledStartAt().isAfter(now)
                        && liveClass.getLiveClassStatus() != LiveClassStatus.CANCELLED
                        && liveClass.getLiveClassStatus() != LiveClassStatus.COMPLETED)
                .count();
    }

    private long countUpcomingTeacherLiveClasses(List<LiveClass> liveClasses) {
        LocalDateTime now = LocalDateTime.now();
        return liveClasses.stream()
                .filter(liveClass -> liveClass.getScheduledStartAt() != null
                        && liveClass.getScheduledStartAt().isAfter(now)
                        && liveClass.getLiveClassStatus() != LiveClassStatus.CANCELLED
                        && liveClass.getLiveClassStatus() != LiveClassStatus.COMPLETED)
                .count();
    }

    private long countTeacherLiveEnrolments(List<LiveClassEnrolment> enrolments, Long teacherId) {
        return enrolments.stream()
                .filter(enrolment -> enrolment.getLiveClass() != null
                        && enrolment.getLiveClass().getTeacher() != null
                        && teacherId.equals(enrolment.getLiveClass().getTeacher().getId())
                        && enrolment.getEnrolmentStatus() != LiveClassEnrolmentStatus.CANCELLED)
                .count();
    }

    private Map<String, Long> initializeContentStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ContentStatus status : ContentStatus.values()) {
            counts.put(status.name(), 0L);
        }
        return counts;
    }

    private void incrementContentStatus(Map<String, Long> counts, ContentStatus status) {
        if (status != null) {
            counts.merge(status.name(), 1L, Long::sum);
        }
    }

    private Map<LocalDate, BigDecimal> initializeRecentDayMap() {
        Map<LocalDate, BigDecimal> countsByDay = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int index = RECENT_ACTIVITY_DAYS - 1; index >= 0; index--) {
            countsByDay.put(today.minusDays(index), BigDecimal.ZERO);
        }
        return countsByDay;
    }

    private void addTrendPoint(Map<LocalDate, BigDecimal> countsByDay, LocalDateTime time, BigDecimal increment) {
        if (time == null) {
            return;
        }
        LocalDate date = time.toLocalDate();
        if (countsByDay.containsKey(date)) {
            countsByDay.put(date, countsByDay.get(date).add(increment));
        }
    }

    private LocalDateTime resolveAttemptTime(QuizAttempt attempt) {
        if (attempt.getCompletedAt() != null) {
            return attempt.getCompletedAt();
        }
        return attempt.getCreatedAt();
    }

    private LocalDateTime resolveLessonProgressTime(LessonProgress progress) {
        if (progress.getLastAccessedAt() != null) {
            return progress.getLastAccessedAt();
        }
        return progress.getUpdatedAt();
    }

    private LocalDateTime resolveVideoProgressTime(VideoProgress progress) {
        if (progress.getLastWatchedAt() != null) {
            return progress.getLastWatchedAt();
        }
        return progress.getUpdatedAt();
    }

    private boolean hasValidPercentage(QuizAttempt attempt) {
        return attempt.isSubmitted()
                && attempt.getScore() != null
                && attempt.getMaxScore() != null
                && attempt.getMaxScore() > 0;
    }

    private BigDecimal percentageForAttempt(QuizAttempt attempt) {
        if (!hasValidPercentage(attempt)) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(attempt.getScore())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(attempt.getMaxScore()), 2, RoundingMode.HALF_UP);
    }

    private boolean isSuccessfulAiLog(AiUsageLog log) {
        return normalizeStatus(log.getStatus()).equals("SUCCESS");
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "UNKNOWN";
        }
        return rawStatus.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = values.stream().reduce(ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private <K, V> List<Map.Entry<K, V>> takeLastEntries(Map<K, V> values, int limit) {
        List<Map.Entry<K, V>> entries = new ArrayList<>(values.entrySet());
        int fromIndex = Math.max(entries.size() - limit, 0);
        return entries.subList(fromIndex, entries.size());
    }

    private BigDecimal scaleDecimal(Double value) {
        if (value == null) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultMoney(BigDecimal amount) {
        if (amount == null) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private List<AnalyticsBreakdownItemResponse> buildBreakdownFromMap(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> new AnalyticsBreakdownItemResponse(entry.getKey(), entry.getValue()))
                .toList();
    }
}
