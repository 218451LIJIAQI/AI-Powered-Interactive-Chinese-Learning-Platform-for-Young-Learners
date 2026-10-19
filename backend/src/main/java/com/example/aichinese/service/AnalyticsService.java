package com.example.aichinese.service;

import static java.math.BigDecimal.ZERO;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.analytics.AdminAnalyticsResponse;
import com.example.aichinese.dto.analytics.AnalyticsBreakdownItemResponse;
import com.example.aichinese.dto.analytics.AnalyticsTrendPointResponse;
import com.example.aichinese.dto.analytics.StudentAnalyticsResponse;
import com.example.aichinese.dto.analytics.TeacherAnalyticsResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
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
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.repository.projection.StatusCountProjection;
import com.example.aichinese.repository.projection.LessonProgressAnalyticsRow;
import com.example.aichinese.repository.projection.QuizAttemptAnalyticsRow;
import com.example.aichinese.repository.projection.VideoProgressAnalyticsRow;
import com.example.aichinese.repository.projection.WrongAnswerCountProjection;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class AnalyticsService {

    private static final int MAX_TREND_POINTS = 6;
    private static final int QUIZ_BATCH_SIZE = 500;
    private static final int RECENT_ACTIVITY_DAYS = 7;
    private static final int DECIMAL_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String UNKNOWN_STATUS = "UNKNOWN";
    private static final Set<LiveClassStatus> CLOSED_LIVE_CLASS_STATUSES =
            EnumSet.of(LiveClassStatus.CANCELLED, LiveClassStatus.COMPLETED);
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

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
    private final LiveClassRepository liveClassRepository;
    private final UserRepository userRepository;
    private final ContentModerationRecordRepository contentModerationRecordRepository;
    private final AuditLogRepository auditLogRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final NotificationService notificationService;

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
            LiveClassRepository liveClassRepository,
            UserRepository userRepository,
            ContentModerationRecordRepository contentModerationRecordRepository,
            AuditLogRepository auditLogRepository,
            AiUsageLogRepository aiUsageLogRepository,
            NotificationService notificationService) {
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
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.contentModerationRecordRepository = Objects.requireNonNull(contentModerationRecordRepository, "contentModerationRecordRepository");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.aiUsageLogRepository = Objects.requireNonNull(aiUsageLogRepository, "aiUsageLogRepository");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
    }

    @Transactional(readOnly = true)
    public StudentAnalyticsResponse getStudentAnalytics(SecurityUserDetails currentUser) {
        StudentProfile student = studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));

        StudentQuizAnalytics quiz = summarizeStudentQuizAttempts(student.getId());
        StudentProgressAnalytics progress = summarizeStudentProgress(student.getId());
        // The query already keeps only ENROLLED, not-yet-started, open classes.
        long upcomingLiveClasses = liveClassEnrolmentRepository
                .findUpcomingForStudent(student.getId(), LiveClassEnrolmentStatus.ENROLLED,
                        LocalDateTime.now(), CLOSED_LIVE_CLASS_STATUSES).size();

        long inProgressLessons = Math.max(progress.totalTrackedLessons() - progress.completedLessons(), 0);

        return new StudentAnalyticsResponse(
                new StudentAnalyticsResponse.Overview(
                        progress.completedLessons(),
                        inProgressLessons,
                        progress.totalTrackedLessons(),
                        quiz.average(),
                        progress.completedVideos(),
                        upcomingLiveClasses,
                        notificationService.countAccessibleUnreadNotifications(currentUser)),
                quiz.trend(),
                buildStudentActivityTrend(progress.recentActivity(), quiz.recentActivity()),
                buildBreakdownFromMap(progress.lessonStatusCounts()),
                buildWeakAreaBreakdown(student),
                buildBreakdownFromMap(progress.learningModeCounts()));
    }

    @Transactional(readOnly = true)
    public TeacherAnalyticsResponse getTeacherAnalytics(SecurityUserDetails currentUser) {
        TeacherProfile teacher = teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));

        Map<String, Long> contentStatusCounts = initializeContentStatusCounts();
        mergeProjectionCounts(contentStatusCounts, learningModuleRepository.groupByContentStatusForTeacher(teacher.getId()));
        mergeProjectionCounts(contentStatusCounts, lessonRepository.groupByContentStatusForTeacher(teacher.getId()));
        mergeProjectionCounts(contentStatusCounts, coursePackageRepository.groupByContentStatusForTeacher(teacher.getId()));
        TeacherQuizAnalytics quiz = summarizeTeacherQuizAttempts(teacher.getId());
        long teacherLiveEnrolmentsCount = liveClassEnrolmentRepository.countActiveByTeacherId(
                teacher.getId(), LiveClassEnrolmentStatus.CANCELLED);

        return new TeacherAnalyticsResponse(
                new TeacherAnalyticsResponse.Overview(
                        learningModuleRepository.countByTeacherId(teacher.getId()),
                        lessonRepository.countByTeacherId(teacher.getId()),
                        coursePackageRepository.countByTeacherId(teacher.getId()),
                        contentStatusCounts.get(ContentStatus.PUBLISHED.name()),
                        contentStatusCounts.get(ContentStatus.SUBMITTED.name()),
                        studentProfileRepository.findLinkedStudentIdsByTeacherId(teacher.getId(), PageRequest.of(0, 1)).getTotalElements(),
                        quiz.average(),
                        liveClassRepository.countByTeacherIdAndScheduledStartAtAfterAndLiveClassStatusNotIn(
                                teacher.getId(), LocalDateTime.now(), CLOSED_LIVE_CLASS_STATUSES),
                        teacherLiveEnrolmentsCount),
                quiz.trend(),
                buildBreakdownFromMap(contentStatusCounts));
    }

    @Transactional(readOnly = true)
    public BigDecimal getTeacherAverageQuizScore(Long teacherId) {
        return summarizeTeacherQuizAttempts(teacherId).average();
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAdminAnalytics(SecurityUserDetails currentUser) {
        // Each count is queried once and shared by the overview and the breakdowns.
        Map<String, Long> teacherCounts = new LinkedHashMap<>();
        for (TeacherStatus status : List.of(TeacherStatus.PENDING, TeacherStatus.APPROVED, TeacherStatus.REJECTED)) {
            teacherCounts.put(status.name(), teacherProfileRepository.countByTeacherStatus(status));
        }
        Map<String, Long> contentCounts = initializeContentStatusCounts();
        mergeProjectionCounts(contentCounts, learningModuleRepository.groupByContentStatus());
        mergeProjectionCounts(contentCounts, lessonRepository.groupByContentStatus());
        mergeProjectionCounts(contentCounts, coursePackageRepository.groupByContentStatus());
        return new AdminAnalyticsResponse(
                new AdminAnalyticsResponse.Overview(
                        userRepository.countByRoleName(RoleName.STUDENT),
                        userRepository.countByRoleName(RoleName.TEACHER),
                        teacherCounts.get(TeacherStatus.PENDING.name()),
                        teacherCounts.get(TeacherStatus.APPROVED.name()),
                        teacherCounts.get(TeacherStatus.REJECTED.name()),
                        contentCounts.get(ContentStatus.PUBLISHED.name()),
                        contentModerationRecordRepository.count(),
                        auditLogRepository.count(),
                        notificationService.countAccessibleUnreadNotifications(currentUser),
                        aiUsageLogRepository.count()),
                buildUserRegistrationTrend(),
                buildBreakdownFromMap(teacherCounts),
                buildBreakdownFromMap(contentCounts),
                buildAiStatusBreakdownFromProjections(aiUsageLogRepository.groupByAiStatus()));
    }

    private StudentQuizAnalytics summarizeStudentQuizAttempts(Long studentId) {
        ScoreAggregate scores = new ScoreAggregate();
        Map<LocalDate, BigDecimal> recentActivity = initializeRecentDayMap();
        Comparator<QuizTrendSample> byTime = Comparator.comparing(QuizTrendSample::time)
                .thenComparing(sample -> sample.completedAt() == null ? 1 : 0)
                .thenComparing(QuizTrendSample::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingLong(QuizTrendSample::id);
        PriorityQueue<QuizTrendSample> latest = new PriorityQueue<>(byTime);
        long afterId = 0;
        while (true) {
            List<QuizAttemptAnalyticsRow> batch = quizAttemptRepository.findStudentAnalyticsBatch(
                    studentId, afterId, PageRequest.of(0, QUIZ_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            afterId = batch.get(batch.size() - 1).getId();
            for (QuizAttemptAnalyticsRow attempt : batch) {
                LocalDateTime time = resolveAttemptTime(attempt);
                addTrendPoint(recentActivity, time, BigDecimal.ONE);
                if (!hasValidPercentage(attempt)) {
                    continue;
                }
                BigDecimal percentage = percentageForAttempt(attempt);
                scores.add(percentage);
                if (time != null) {
                    latest.add(new QuizTrendSample(
                            attempt.getId(), time, attempt.getCompletedAt(), attempt.getCreatedAt(), percentage));
                    if (latest.size() > MAX_TREND_POINTS) {
                        latest.poll();
                    }
                }
            }
            if (batch.size() < QUIZ_BATCH_SIZE) {
                break;
            }
        }
        List<AnalyticsTrendPointResponse> trend = latest.stream()
                .sorted(byTime)
                .map(sample -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(sample.time().toLocalDate()), sample.percentage()))
                .toList();
        return new StudentQuizAnalytics(scores.average(), trend, recentActivity);
    }

    private TeacherQuizAnalytics summarizeTeacherQuizAttempts(Long teacherId) {
        ScoreAggregate scores = new ScoreAggregate();
        TreeMap<LocalDate, ScoreAggregate> latestDays = new TreeMap<>();
        long afterId = 0;
        while (true) {
            List<QuizAttemptAnalyticsRow> batch = quizAttemptRepository.findTeacherAnalyticsBatch(
                    teacherId, afterId, PageRequest.of(0, QUIZ_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            afterId = batch.get(batch.size() - 1).getId();
            for (QuizAttemptAnalyticsRow attempt : batch) {
                if (!hasValidPercentage(attempt)) {
                    continue;
                }
                BigDecimal percentage = percentageForAttempt(attempt);
                scores.add(percentage);
                LocalDateTime time = resolveAttemptTime(attempt);
                if (time == null) {
                    continue;
                }
                LocalDate day = time.toLocalDate();
                if (latestDays.size() < MAX_TREND_POINTS || day.compareTo(latestDays.firstKey()) >= 0) {
                    latestDays.computeIfAbsent(day, ignored -> new ScoreAggregate()).add(percentage);
                    if (latestDays.size() > MAX_TREND_POINTS) {
                        latestDays.pollFirstEntry();
                    }
                }
            }
            if (batch.size() < QUIZ_BATCH_SIZE) {
                break;
            }
        }
        List<AnalyticsTrendPointResponse> trend = latestDays.entrySet().stream()
                .map(entry -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(entry.getKey()), entry.getValue().average()))
                .toList();
        return new TeacherQuizAnalytics(scores.average(), trend);
    }

    private record QuizTrendSample(
            Long id, LocalDateTime time, LocalDateTime completedAt,
            LocalDateTime createdAt, BigDecimal percentage) { }

    private record StudentQuizAnalytics(
            BigDecimal average, List<AnalyticsTrendPointResponse> trend,
            Map<LocalDate, BigDecimal> recentActivity) { }

    private record TeacherQuizAnalytics(BigDecimal average, List<AnalyticsTrendPointResponse> trend) { }

    private static final class ScoreAggregate {
        private BigDecimal sum = ZERO;
        private long count;

        void add(BigDecimal percentage) {
            sum = sum.add(percentage);
            count++;
        }

        BigDecimal average() {
            return count == 0
                    ? ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)
                    : sum.divide(BigDecimal.valueOf(count), DECIMAL_SCALE, RoundingMode.HALF_UP);
        }
    }

    private StudentProgressAnalytics summarizeStudentProgress(Long studentId) {
        long completedLessons = 0;
        long totalTrackedLessons = 0;
        long completedVideos = 0;
        Map<LocalDate, BigDecimal> recentActivity = initializeRecentDayMap();
        Map<String, Long> lessonStatusCounts = new LinkedHashMap<>();
        lessonStatusCounts.put("COMPLETED", 0L);
        lessonStatusCounts.put("IN_PROGRESS", 0L);
        Map<String, Long> learningModeCounts = new LinkedHashMap<>();

        long afterLessonProgressId = 0;
        while (true) {
            List<LessonProgressAnalyticsRow> batch = lessonProgressRepository.findAnalyticsBatch(
                    studentId, afterLessonProgressId, PageRequest.of(0, QUIZ_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            afterLessonProgressId = batch.get(batch.size() - 1).getId();
            for (LessonProgressAnalyticsRow row : batch) {
                totalTrackedLessons++;
                if (row.isCompleted()) {
                    completedLessons++;
                    lessonStatusCounts.merge("COMPLETED", 1L, Long::sum);
                } else {
                    lessonStatusCounts.merge("IN_PROGRESS", 1L, Long::sum);
                }
                if (row.getLearningType() != null) {
                    learningModeCounts.merge(row.getLearningType().name(), 1L, Long::sum);
                }
                addTrendPoint(recentActivity,
                        row.getLastAccessedAt() != null ? row.getLastAccessedAt() : row.getUpdatedAt(),
                        BigDecimal.ONE);
            }
            if (batch.size() < QUIZ_BATCH_SIZE) {
                break;
            }
        }

        long afterVideoProgressId = 0;
        while (true) {
            List<VideoProgressAnalyticsRow> batch = videoProgressRepository.findAnalyticsBatch(
                    studentId, afterVideoProgressId, PageRequest.of(0, QUIZ_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            afterVideoProgressId = batch.get(batch.size() - 1).getId();
            for (VideoProgressAnalyticsRow row : batch) {
                if (row.isCompleted()) {
                    completedVideos++;
                }
                addTrendPoint(recentActivity,
                        row.getLastWatchedAt() != null ? row.getLastWatchedAt() : row.getUpdatedAt(),
                        BigDecimal.ONE);
            }
            if (batch.size() < QUIZ_BATCH_SIZE) {
                break;
            }
        }

        return new StudentProgressAnalytics(
                completedLessons, totalTrackedLessons, completedVideos,
                recentActivity, lessonStatusCounts, learningModeCounts);
    }

    private record StudentProgressAnalytics(
            long completedLessons,
            long totalTrackedLessons,
            long completedVideos,
            Map<LocalDate, BigDecimal> recentActivity,
            Map<String, Long> lessonStatusCounts,
            Map<String, Long> learningModeCounts) { }

    private List<AnalyticsTrendPointResponse> buildStudentActivityTrend(
            Map<LocalDate, BigDecimal> progressActivity,
            Map<LocalDate, BigDecimal> quizActivity) {
        Map<LocalDate, BigDecimal> countsByDay = initializeRecentDayMap();

        progressActivity.forEach((day, count) -> countsByDay.merge(day, count, BigDecimal::add));
        quizActivity.forEach((day, count) -> countsByDay.merge(day, count, BigDecimal::add));

        return countsByDay.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(ZERO) > 0)
                .map(entry -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private List<AnalyticsBreakdownItemResponse> buildWeakAreaBreakdown(StudentProfile student) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (WrongAnswerCountProjection item : quizAttemptAnswerRepository
                .countWrongAnswersByQuestionType(student.getId())) {
            if (item.getQuestionType() != null && !item.getQuestionType().isBlank()) {
                counts.put(item.getQuestionType(), item.getAnswerCount());
            }
        }
        return buildBreakdownFromMap(counts);
    }

    private List<AnalyticsTrendPointResponse> buildUserRegistrationTrend() {
        Map<LocalDate, BigDecimal> countsByDay = initializeRecentDayMap();
        countsByDay.replaceAll((day, ignored) -> BigDecimal.valueOf(
                userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        day.atStartOfDay(), day.plusDays(1).atStartOfDay())));

        return countsByDay.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(ZERO) > 0)
                .map(entry -> new AnalyticsTrendPointResponse(
                        DAY_LABEL_FORMATTER.format(entry.getKey()),
                        entry.getValue()))
                .toList();
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

    private LocalDateTime resolveAttemptTime(QuizAttemptAnalyticsRow attempt) {
        if (attempt.getCompletedAt() != null) {
            return attempt.getCompletedAt();
        }
        return attempt.getCreatedAt();
    }

    private boolean hasValidPercentage(QuizAttemptAnalyticsRow attempt) {
        return attempt.isSubmitted()
                && attempt.getScore() != null
                && attempt.getMaxScore() != null
                && attempt.getMaxScore() > 0;
    }

    /** Callers skip attempts without a valid percentage first. */
    private BigDecimal percentageForAttempt(QuizAttemptAnalyticsRow attempt) {
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

    private List<AnalyticsBreakdownItemResponse> buildBreakdownFromMap(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> new AnalyticsBreakdownItemResponse(entry.getKey(), entry.getValue()))
                .toList();
    }
}
