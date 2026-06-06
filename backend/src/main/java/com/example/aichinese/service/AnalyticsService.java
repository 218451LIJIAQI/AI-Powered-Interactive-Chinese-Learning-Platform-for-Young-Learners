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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.analytics.AdminAnalyticsResponse;
import com.example.aichinese.dto.analytics.AnalyticsBreakdownItemResponse;
import com.example.aichinese.dto.analytics.AnalyticsTrendPointResponse;
import com.example.aichinese.dto.analytics.StudentAnalyticsResponse;
import com.example.aichinese.dto.analytics.TeacherAnalyticsResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.entity.enums.RefundStatus;
import com.example.aichinese.entity.enums.RoleName;
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
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.RefundRequestRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.repository.projection.StatusCountProjection;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class AnalyticsService {

    private static final int MAX_TREND_POINTS = 6;
    private static final int RECENT_ACTIVITY_DAYS = 7;
    private static final int DECIMAL_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String UNKNOWN_STATUS = "UNKNOWN";
    private static final Set<LiveClassStatus> CLOSED_LIVE_CLASS_STATUSES =
            EnumSet.of(LiveClassStatus.CANCELLED, LiveClassStatus.COMPLETED);
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final TeacherEarningRepository teacherEarningRepository;
    private final LiveClassRepository liveClassRepository;
    private final UserRepository userRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final ContentModerationRecordRepository contentModerationRecordRepository;
    private final AuditLogRepository auditLogRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final LiveLearningAccessService liveLearningAccessService;
    private final NotificationService notificationService;
    private final CommerceSettingsService commerceSettingsService;

    public AnalyticsService(
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            LessonProgressRepository lessonProgressRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            VideoProgressRepository videoProgressRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            LearningModuleRepository learningModuleRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            TeacherEarningRepository teacherEarningRepository,
            LiveClassRepository liveClassRepository,
            UserRepository userRepository,
            PaymentRecordRepository paymentRecordRepository,
            RefundRequestRepository refundRequestRepository,
            PayoutRequestRepository payoutRequestRepository,
            ContentModerationRecordRepository contentModerationRecordRepository,
            AuditLogRepository auditLogRepository,
            AiUsageLogRepository aiUsageLogRepository,
            StudentLearningAccessService studentLearningAccessService,
            LiveLearningAccessService liveLearningAccessService,
            NotificationService notificationService,
            CommerceSettingsService commerceSettingsService) {
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.videoProgressRepository = Objects.requireNonNull(videoProgressRepository, "videoProgressRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.teacherEarningRepository = Objects.requireNonNull(teacherEarningRepository, "teacherEarningRepository");
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository, "paymentRecordRepository");
        this.refundRequestRepository = Objects.requireNonNull(refundRequestRepository, "refundRequestRepository");
        this.payoutRequestRepository = Objects.requireNonNull(payoutRequestRepository, "payoutRequestRepository");
        this.contentModerationRecordRepository = Objects.requireNonNull(contentModerationRecordRepository, "contentModerationRecordRepository");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.aiUsageLogRepository = Objects.requireNonNull(aiUsageLogRepository, "aiUsageLogRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.commerceSettingsService = Objects.requireNonNull(commerceSettingsService, "commerceSettingsService");
    }

    @Transactional(readOnly = true)
    public StudentAnalyticsResponse getStudentAnalytics(SecurityUserDetails currentUser) {
        StudentProfile student = studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));

        List<QuizAttempt> attempts = quizAttemptRepository.findByStudentIdOrderByCompletedAtDescCreatedAtDesc(student.getId()).stream()
                .filter(attempt -> hasCurrentStudentAttemptAccess(student, attempt))
                .toList();
        List<LessonProgress> lessonProgressList = lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId()).stream()
                .filter(progress -> hasCurrentLessonProgressAccess(student, progress))
                .toList();
        List<VideoProgress> videoProgressList = videoProgressRepository.findByStudentIdOrderByLastWatchedAtDesc(student.getId()).stream()
                .filter(progress -> hasCurrentVideoProgressAccess(student, progress))
                .toList();
        List<LiveClassEnrolment> enrolments = liveClassEnrolmentRepository
                .findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(student.getId(), LiveClassEnrolmentStatus.ENROLLED).stream()
                .filter(this::hasCurrentTeacherLiveEnrolmentAccess)
                .toList();

        long completedLessons = lessonProgressList.stream().filter(LessonProgress::isCompleted).count();
        long totalTrackedLessons = lessonProgressList.size();
        long inProgressLessons = Math.max(totalTrackedLessons - completedLessons, 0);

        return new StudentAnalyticsResponse(
                new StudentAnalyticsResponse.Overview(
                        completedLessons,
                        inProgressLessons,
                        totalTrackedLessons,
                        averageAccessibleAttemptScore(attempts),
                        videoProgressList.stream().filter(VideoProgress::isCompleted).count(),
                        countUpcomingStudentLiveClasses(enrolments),
                        notificationService.countAccessibleUnreadNotifications(currentUser)),
                buildQuizTrend(attempts),
                buildStudentActivityTrend(lessonProgressList, attempts, videoProgressList),
                buildStudentLessonStatusBreakdown(lessonProgressList),
                buildWeakAreaBreakdown(student),
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
        List<QuizAttempt> accessibleAttempts = attempts.stream()
                .filter(this::hasCurrentTeacherAttemptAccess)
                .toList();
        long teacherLiveEnrolmentsCount = liveClassEnrolmentRepository
                .findByLiveClassTeacherIdAndEnrolmentStatusNot(teacher.getId(), LiveClassEnrolmentStatus.CANCELLED).stream()
                .filter(this::hasCurrentTeacherLiveEnrolmentAccess)
                .count();

        return new TeacherAnalyticsResponse(
                new TeacherAnalyticsResponse.Overview(
                        modules.size(),
                        lessons.size(),
                        packages.size(),
                        countByStatus(modules, lessons, packages, ContentStatus.PUBLISHED),
                        countByStatus(modules, lessons, packages, ContentStatus.SUBMITTED),
                        studentProfileRepository.findLinkedStudentIdsByTeacherId(teacher.getId(), PageRequest.of(0, 1)).getTotalElements(),
                        averageAccessibleAttemptScore(accessibleAttempts),
                        defaultMoney(teacherEarningRepository.sumNetAmountByTeacherId(teacher.getId())),
                        defaultMoney(teacherEarningRepository.sumAvailableNetAmountByTeacherId(teacher.getId())),
                        commerceSettingsService.getDefaultCurrency(),
                        countUpcomingTeacherLiveClasses(liveClasses),
                        teacherLiveEnrolmentsCount),
                buildTeacherQuizTrend(accessibleAttempts),
                buildTeacherEarningsTrend(earnings),
                buildTeacherContentStatusBreakdown(modules, lessons, packages));
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAdminAnalytics(SecurityUserDetails currentUser) {
        LocalDateTime trendWindowStart = LocalDate.now()
                .minusDays(RECENT_ACTIVITY_DAYS - 1L)
                .atStartOfDay();
        List<User> recentUsers = userRepository
                .findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(trendWindowStart);

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
                        commerceSettingsService.getDefaultCurrency(),
                        refundRequestRepository.countByRefundStatus(RefundStatus.PENDING),
                        payoutRequestRepository.countByPayoutStatus(PayoutStatus.PENDING),
                        contentModerationRecordRepository.count(),
                        auditLogRepository.count(),
                        notificationService.countAccessibleUnreadNotifications(currentUser),
                        aiUsageLogRepository.count()),
                buildUserRegistrationTrend(recentUsers),
                buildTeacherApprovalBreakdown(),
                buildContentStatusBreakdownFromProjections(
                        learningModuleRepository.groupByContentStatus(),
                        lessonRepository.groupByContentStatus(),
                        coursePackageRepository.groupByContentStatus()),
                buildBreakdownFromProjections(
                        paymentRecordRepository.groupByPaymentStatus(),
                        enumNames(PaymentStatus.class)),
                buildAiStatusBreakdownFromProjections(aiUsageLogRepository.groupByAiStatus()));
    }

    private List<AnalyticsTrendPointResponse> buildQuizTrend(List<QuizAttempt> attempts) {
        List<QuizAttempt> validSortedAttempts = attempts.stream()
                .filter(this::hasValidPercentage)
                .filter(attempt -> resolveAttemptTime(attempt) != null)
                .sorted(Comparator.comparing(this::resolveAttemptTime))
                .toList();
        long skipCount = Math.max(0, validSortedAttempts.size() - MAX_TREND_POINTS);
        return validSortedAttempts.stream()
                .skip(skipCount)
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
                .filter(entry -> entry.getValue().compareTo(ZERO) > 0)
                .map(entry -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private List<AnalyticsBreakdownItemResponse> buildStudentLessonStatusBreakdown(List<LessonProgress> lessonProgressList) {
        long completed = lessonProgressList.stream().filter(LessonProgress::isCompleted).count();
        long inProgress = lessonProgressList.size() - completed;
        return buildBreakdownFromMap(Map.of(
                "COMPLETED", completed,
                "IN_PROGRESS", inProgress));
    }

    private List<AnalyticsBreakdownItemResponse> buildWeakAreaBreakdown(StudentProfile student) {
        Map<String, Long> counts = new LinkedHashMap<>();
        quizAttemptAnswerRepository.findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(student.getId()).stream()
                .filter(answer -> hasCurrentWrongAnswerAccess(student, answer))
                .map(this::questionType)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> counts.merge(value, 1L, (a, b) -> a + b));
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildStudentLearningModeBreakdown(List<LessonProgress> lessonProgressList) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LessonProgress progress : lessonProgressList) {
            Lesson lesson = progress.getLesson();
            if (lesson == null || lesson.getLearningType() == null) {
                continue;
            }
            counts.merge(lesson.getLearningType().name(), 1L, (a, b) -> a + b);
        }
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsTrendPointResponse> buildTeacherQuizTrend(List<QuizAttempt> attempts) {
        Map<LocalDate, List<BigDecimal>> grouped = new LinkedHashMap<>();
        attempts.stream()
                .filter(this::hasValidPercentage)
                .filter(attempt -> resolveAttemptTime(attempt) != null)
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

    private boolean hasCurrentTeacherAttemptAccess(QuizAttempt attempt) {
        if (attempt.getStudent() == null
                || attempt.getQuiz() == null
                || attempt.getQuiz().getLesson() == null) {
            return true;
        }
        return studentLearningAccessService.hasLessonAccess(attempt.getStudent(), attempt.getQuiz().getLesson());
    }

    private boolean hasCurrentStudentAttemptAccess(StudentProfile student, QuizAttempt attempt) {
        return attempt.getQuiz() == null
                || attempt.getQuiz().getLesson() == null
                || studentLearningAccessService.hasLessonAccess(student, attempt.getQuiz().getLesson());
    }

    private boolean hasCurrentLessonProgressAccess(StudentProfile student, LessonProgress progress) {
        return progress.getLesson() == null || studentLearningAccessService.hasLessonAccess(student, progress.getLesson());
    }

    private boolean hasCurrentVideoProgressAccess(StudentProfile student, VideoProgress progress) {
        Lesson lesson = progress.getLesson();
        CoursePackage coursePackage = progress.getRecordedVideo() != null ? progress.getRecordedVideo().getCoursePackage() : null;
        boolean lessonAccess = lesson == null || liveLearningAccessService.hasStudentLessonAccess(student, lesson);
        boolean packageAccess = coursePackage == null || liveLearningAccessService.hasStudentPackageAccess(student, coursePackage);
        return lessonAccess && packageAccess;
    }

    private boolean hasCurrentWrongAnswerAccess(StudentProfile student, QuizAttemptAnswer answer) {
        return answer.getQuestion() != null
                && answer.getQuestion().getQuiz() != null
                && answer.getQuestion().getQuiz().getLesson() != null
                && studentLearningAccessService.hasLessonAccess(student, answer.getQuestion().getQuiz().getLesson());
    }

    private String questionType(QuizAttemptAnswer answer) {
        return answer.getQuestion() == null ? null : answer.getQuestion().getQuestionType();
    }

    private boolean hasCurrentTeacherLiveEnrolmentAccess(LiveClassEnrolment enrolment) {
        if (enrolment.getStudent() == null || enrolment.getLiveClass() == null) {
            return false;
        }
        Lesson lesson = enrolment.getLiveClass().getLesson();
        CoursePackage coursePackage = enrolment.getLiveClass().getCoursePackage();
        boolean lessonAccess = lesson == null || liveLearningAccessService.hasStudentLessonAccess(enrolment.getStudent(), lesson);
        boolean packageAccess = coursePackage == null || liveLearningAccessService.hasStudentPackageAccess(enrolment.getStudent(), coursePackage);
        return lessonAccess && packageAccess;
    }

    private BigDecimal averageAccessibleAttemptScore(List<QuizAttempt> attempts) {
        List<BigDecimal> percentages = attempts.stream()
                .filter(this::hasValidPercentage)
                .map(this::percentageForAttempt)
                .toList();
        return average(percentages);
    }

    private List<AnalyticsTrendPointResponse> buildTeacherEarningsTrend(List<TeacherEarning> earnings) {
        Map<YearMonth, BigDecimal> grouped = new LinkedHashMap<>();
        earnings.stream()
                .filter(earning -> earning.getCreatedAt() != null)
                .sorted(Comparator.comparing(TeacherEarning::getCreatedAt))
                .forEach(earning -> grouped.merge(
                        YearMonth.from(earning.getCreatedAt()),
                        defaultMoney(earning.getNetAmount()),
                        BigDecimal::add));

        return takeLastEntries(grouped, MAX_TREND_POINTS).stream()
                .map(entry -> new AnalyticsTrendPointResponse(
                        MONTH_LABEL_FORMATTER.format(entry.getKey().atDay(1)),
                        entry.getValue().setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)))
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

    private List<AnalyticsTrendPointResponse> buildUserRegistrationTrend(List<User> users) {
        Map<LocalDate, BigDecimal> countsByDay = initializeRecentDayMap();
        users.forEach(user -> addTrendPoint(countsByDay, user.getCreatedAt(), BigDecimal.ONE));

        return countsByDay.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(ZERO) > 0)
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

    private List<AnalyticsBreakdownItemResponse> buildBreakdownFromProjections(
            List<StatusCountProjection> projections,
            String[] expectedStatuses) {
        Map<String, Long> counts = initializeCounts(expectedStatuses);
        mergeProjectionCounts(counts, projections);
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsBreakdownItemResponse> buildContentStatusBreakdownFromProjections(
            List<StatusCountProjection> moduleCounts,
            List<StatusCountProjection> lessonCounts,
            List<StatusCountProjection> packageCounts) {
        Map<String, Long> counts = initializeContentStatusCounts();
        mergeProjectionCounts(counts, moduleCounts);
        mergeProjectionCounts(counts, lessonCounts);
        mergeProjectionCounts(counts, packageCounts);
        return buildBreakdownFromMap(counts);
    }

    private static void mergeProjectionCounts(Map<String, Long> counts, List<StatusCountProjection> projections) {
        for (StatusCountProjection projection : projections) {
            if (projection.getStatus() != null) {
                counts.merge(projection.getStatus(), projection.getCount(), Long::sum);
            }
        }
    }

    private List<AnalyticsBreakdownItemResponse> buildAiStatusBreakdownFromProjections(
            List<StatusCountProjection> projections) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (StatusCountProjection projection : projections) {
            counts.merge(normalizeStatus(projection.getStatus()), projection.getCount(), Long::sum);
        }
        return buildBreakdownFromMap(counts);
    }

    private static String[] enumNames(Class<? extends Enum<?>> enumClass) {
        Enum<?>[] constants = enumClass.getEnumConstants();
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].name();
        }
        return names;
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

    private long countUpcomingStudentLiveClasses(List<LiveClassEnrolment> enrolments) {
        LocalDateTime now = LocalDateTime.now();
        return enrolments.stream()
                .map(LiveClassEnrolment::getLiveClass)
                .filter(liveClass -> isUpcomingLiveClass(liveClass, now))
                .count();
    }

    private long countUpcomingTeacherLiveClasses(List<LiveClass> liveClasses) {
        LocalDateTime now = LocalDateTime.now();
        return liveClasses.stream()
                .filter(liveClass -> isUpcomingLiveClass(liveClass, now))
                .count();
    }

    private Map<String, Long> initializeContentStatusCounts() {
        return initializeCounts(enumNames(ContentStatus.class));
    }

    private static Map<String, Long> initializeCounts(String[] statuses) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : statuses) {
            counts.put(status, 0L);
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
            countsByDay.put(today.minusDays(index), ZERO);
        }
        return countsByDay;
    }

    private void addTrendPoint(Map<LocalDate, BigDecimal> countsByDay, LocalDateTime time, BigDecimal increment) {
        if (time == null) {
            return;
        }
        countsByDay.computeIfPresent(time.toLocalDate(), (ignored, current) -> current.add(increment));
    }

    private boolean isUpcomingLiveClass(LiveClass liveClass, LocalDateTime now) {
        return liveClass != null
                && liveClass.getScheduledStartAt() != null
                && liveClass.getScheduledStartAt().isAfter(now)
                && !CLOSED_LIVE_CLASS_STATUSES.contains(liveClass.getLiveClassStatus());
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
            return ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(attempt.getScore())
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(attempt.getMaxScore()), DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return UNKNOWN_STATUS;
        }
        return rawStatus.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal total = values.stream().reduce(ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private <K, V> List<Map.Entry<K, V>> takeLastEntries(Map<K, V> values, int limit) {
        List<Map.Entry<K, V>> entries = new ArrayList<>(values.entrySet());
        int fromIndex = Math.max(entries.size() - limit, 0);
        return entries.subList(fromIndex, entries.size());
    }

    private BigDecimal defaultMoney(BigDecimal amount) {
        if (amount == null) {
            return ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
        }
        return amount.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private List<AnalyticsBreakdownItemResponse> buildBreakdownFromMap(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> new AnalyticsBreakdownItemResponse(entry.getKey(), entry.getValue()))
                .toList();
    }
}
