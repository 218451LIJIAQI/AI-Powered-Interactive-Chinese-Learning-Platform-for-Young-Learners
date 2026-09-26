package com.example.aichinese.service;

import static java.math.BigDecimal.ZERO;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.dashboard.AdminDashboardResponse;
import com.example.aichinese.dto.dashboard.StudentDashboardResponse;
import com.example.aichinese.dto.dashboard.TeacherDashboardResponse;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.entity.enums.RefundStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RefundRequestRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class DashboardService {

    private static final int RECENT_ITEM_LIMIT = 5;
    private static final int MONEY_SCALE = 2;

    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final DailyStreakRepository dailyStreakRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LessonRepository lessonRepository;
    private final TeacherEarningRepository teacherEarningRepository;
    private final UserRepository userRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final QuizRepository quizRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final FlashcardRepository flashcardRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final NotificationService notificationService;
    private final CommerceSettingsService commerceSettingsService;
    private final AnalyticsService analyticsService;

    public DashboardService(
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            DailyStreakRepository dailyStreakRepository,
            StudentBadgeRepository studentBadgeRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            LessonProgressRepository lessonProgressRepository,
            LessonRepository lessonRepository,
            TeacherEarningRepository teacherEarningRepository,
            UserRepository userRepository,
            LearningModuleRepository learningModuleRepository,
            QuizRepository quizRepository,
            MatchingGameRepository matchingGameRepository,
            FlashcardRepository flashcardRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            AiUsageLogRepository aiUsageLogRepository,
            PaymentRecordRepository paymentRecordRepository,
            RefundRequestRepository refundRequestRepository,
            PayoutRequestRepository payoutRequestRepository,
            AuditLogRepository auditLogRepository,
            StudentLearningAccessService studentLearningAccessService,
            NotificationService notificationService,
            CommerceSettingsService commerceSettingsService,
            AnalyticsService analyticsService) {
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.dailyStreakRepository = Objects.requireNonNull(dailyStreakRepository, "dailyStreakRepository");
        this.studentBadgeRepository = Objects.requireNonNull(studentBadgeRepository, "studentBadgeRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.teacherEarningRepository = Objects.requireNonNull(teacherEarningRepository, "teacherEarningRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.matchingGameRepository = Objects.requireNonNull(matchingGameRepository, "matchingGameRepository");
        this.flashcardRepository = Objects.requireNonNull(flashcardRepository, "flashcardRepository");
        this.listeningExerciseRepository = Objects.requireNonNull(listeningExerciseRepository, "listeningExerciseRepository");
        this.tracingExerciseRepository = Objects.requireNonNull(tracingExerciseRepository, "tracingExerciseRepository");
        this.aiUsageLogRepository = Objects.requireNonNull(aiUsageLogRepository, "aiUsageLogRepository");
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository, "paymentRecordRepository");
        this.refundRequestRepository = Objects.requireNonNull(refundRequestRepository, "refundRequestRepository");
        this.payoutRequestRepository = Objects.requireNonNull(payoutRequestRepository, "payoutRequestRepository");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.commerceSettingsService = Objects.requireNonNull(commerceSettingsService, "commerceSettingsService");
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService");
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse getStudentDashboard(SecurityUserDetails currentUser) {
        StudentProfile studentProfile = studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));

        List<LessonProgress> accessibleLessonProgress = lessonProgressRepository
                .findByStudentIdOrderByUpdatedAtDesc(studentProfile.getId()).stream()
                .filter(progress -> hasCurrentLessonProgressAccess(studentProfile, progress))
                .toList();

        String currentLevel = studentProfile.getCurrentLevel() != null
                ? studentProfile.getCurrentLevel().getName()
                : "Beginner";
        int progressPercent = overallCompletionPercent(accessibleLessonProgress);
        int dailyStreak = dailyStreakRepository.findByStudentId(studentProfile.getId())
                .map(streak -> streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0)
                .orElse(0);
        long badgeCount = studentBadgeRepository.countByStudentId(studentProfile.getId());

        List<StudentDashboardResponse.RecentQuizResult> recentQuizResults = quizAttemptRepository
                .findRecentAccessibleForStudent(studentProfile.getId(), AccessType.PAID,
                        PageRequest.of(0, RECENT_ITEM_LIMIT)).stream()
                .map(attempt -> new StudentDashboardResponse.RecentQuizResult(
                        attempt.getId(),
                        attempt.getQuiz() != null ? attempt.getQuiz().getId() : null,
                        attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : "Practice Quiz",
                        attempt.getScore(),
                        attempt.getMaxScore(),
                        attempt.getCompletedAt()))
                .toList();

        List<String> weakAreas = extractWeakAreas(studentProfile);

        List<StudentDashboardResponse.LessonProgressSummary> unfinishedLessons = accessibleLessonProgress.stream()
                .filter(progress -> !progress.isCompleted())
                .filter(progress -> progress.getLesson() != null)
                .limit(RECENT_ITEM_LIMIT)
                .map(progress -> new StudentDashboardResponse.LessonProgressSummary(
                        progress.getLesson().getId(),
                        progress.getLesson().getTitle(),
                        progress.getCompletionPercent(),
                        progress.getLastAccessedAt()))
                .toList();

        List<StudentDashboardResponse.NotificationSummary> notifications = mapStudentNotifications(
                notificationService.findRecentAccessibleNotifications(currentUser, RECENT_ITEM_LIMIT));

        return new StudentDashboardResponse(
                currentUser.getDisplayName(),
                currentLevel,
                progressPercent,
                dailyStreak,
                studentProfile.getTotalPoints() != null ? studentProfile.getTotalPoints() : 0,
                badgeCount,
                recentQuizResults,
                weakAreas,
                unfinishedLessons,
                notifications,
                buildStudentRecommendation(weakAreas, progressPercent));
    }

    @Transactional(readOnly = true)
    public TeacherDashboardResponse getTeacherDashboard(SecurityUserDetails currentUser) {
        TeacherProfile teacherProfile = teacherProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));

        BigDecimal grossAmount = defaultMoney(teacherEarningRepository.sumGrossAmountByTeacherId(teacherProfile.getId()));
        BigDecimal netAmount = defaultMoney(teacherEarningRepository.sumNetAmountByTeacherId(teacherProfile.getId()));
        BigDecimal averageQuizScore = analyticsService.getTeacherAverageQuizScore(teacherProfile.getId());

        List<TeacherDashboardResponse.RecentQuizResult> recentQuizResults = quizAttemptRepository
                .findRecentAccessibleForTeacher(teacherProfile.getId(), AccessType.PAID,
                        PageRequest.of(0, RECENT_ITEM_LIMIT)).stream()
                .map(attempt -> new TeacherDashboardResponse.RecentQuizResult(
                        attempt.getId(),
                        attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : "Practice Quiz",
                        studentDisplayName(attempt),
                        attempt.getScore(),
                        attempt.getMaxScore(),
                        attempt.getCompletedAt()))
                .toList();

        List<TeacherDashboardResponse.NotificationSummary> notifications = notificationService
                .findRecentAccessibleNotifications(currentUser, RECENT_ITEM_LIMIT).stream()
                .map(DashboardService::mapTeacherNotification)
                .toList();

        return new TeacherDashboardResponse(
                currentUser.getDisplayName(),
                teacherProfile.getTeacherStatus(),
                lessonRepository.countByTeacherId(teacherProfile.getId()),
                studentProfileRepository.findLinkedStudentIdsByTeacherId(teacherProfile.getId(), PageRequest.of(0, 1)).getTotalElements(),
                averageQuizScore,
                new TeacherDashboardResponse.EarningsSummary(
                        grossAmount,
                        netAmount,
                        netAmount,
                        zeroMoney(),
                        commerceSettingsService.getDefaultCurrency()),
                recentQuizResults,
                notifications);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard(SecurityUserDetails currentUser) {
        long totalGames = matchingGameRepository.count()
                + flashcardRepository.count()
                + listeningExerciseRepository.count()
                + tracingExerciseRepository.count();

        List<AdminDashboardResponse.RecentUserActivity> recentUserActivity = userRepository
                .findTop5ByOrderByCreatedAtDesc().stream()
                .map(user -> new AdminDashboardResponse.RecentUserActivity(
                        user.getId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getRoles().stream()
                                .map(role -> role.getName().name())
                                .sorted(Comparator.naturalOrder())
                                .toList(),
                        user.getCreatedAt()))
                .toList();

        List<AdminDashboardResponse.RecentAuditLogSummary> recentAuditLogs = auditLogRepository
                .findTop5ByOrderByCreatedAtDesc().stream()
                .map(log -> new AdminDashboardResponse.RecentAuditLogSummary(
                        log.getId(),
                        log.getActionType(),
                        log.getTargetType(),
                        log.getDescription(),
                        log.getCreatedAt()))
                .toList();

        return new AdminDashboardResponse(
                currentUser.getDisplayName(),
                studentProfileRepository.count(),
                teacherProfileRepository.count(),
                teacherProfileRepository.countByTeacherStatus(TeacherStatus.PENDING),
                learningModuleRepository.count(),
                lessonRepository.count(),
                quizRepository.count(),
                totalGames,
                recentUserActivity,
                new AdminDashboardResponse.AiUsageSummary(
                        aiUsageLogRepository.count(),
                        aiUsageLogRepository.countByStatusIgnoreCase("SUCCESS")),
                new AdminDashboardResponse.PaymentRevenueSummary(
                        paymentRecordRepository.count(),
                        paymentRecordRepository.countByPaymentStatus(PaymentStatus.PAID),
                        defaultMoney(paymentRecordRepository.sumAmountByPaymentStatus(PaymentStatus.PAID)),
                        commerceSettingsService.getDefaultCurrency()),
                refundRequestRepository.countByRefundStatus(RefundStatus.PENDING),
                payoutRequestRepository.countByPayoutStatus(PayoutStatus.PENDING),
                recentAuditLogs);
    }

    private static List<StudentDashboardResponse.NotificationSummary> mapStudentNotifications(List<Notification> notifications) {
        return notifications.stream()
                .map(notification -> new StudentDashboardResponse.NotificationSummary(
                        notification.getId(),
                        notification.getTitle(),
                        notification.getMessage(),
                        notification.getNotificationStatus().name(),
                        notification.getTargetPath(),
                        notification.getCreatedAt()))
                .toList();
    }

    private static TeacherDashboardResponse.NotificationSummary mapTeacherNotification(Notification notification) {
        return new TeacherDashboardResponse.NotificationSummary(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationStatus().name(),
                notification.getTargetPath(),
                notification.getCreatedAt());
    }

    private List<String> extractWeakAreas(StudentProfile student) {
        return quizAttemptAnswerRepository.findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(student.getId()).stream()
                .filter(answer -> hasCurrentWrongAnswerAccess(student, answer))
                .map(answer -> answer.getQuestion() == null ? null : answer.getQuestion().getQuestionType())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(RECENT_ITEM_LIMIT)
                .toList();
    }

    private static String buildStudentRecommendation(List<String> weakAreas, int progressPercent) {
        if (!weakAreas.isEmpty()) {
            return "Focus on " + weakAreas.get(0) + " with short daily practice before moving to new topics.";
        }

        if (progressPercent == 0) {
            return "Start with beginner vocabulary, pinyin sounds, and one short lesson to build your learning streak.";
        }

        return "Keep going with your next beginner lesson and review one previous activity to strengthen retention.";
    }

    private boolean hasCurrentLessonProgressAccess(StudentProfile student, LessonProgress progress) {
        return progress.getLesson() == null || studentLearningAccessService.hasLessonAccess(student, progress.getLesson());
    }

    private boolean hasCurrentWrongAnswerAccess(StudentProfile student, QuizAttemptAnswer answer) {
        return answer.getQuestion() != null
                && answer.getQuestion().getQuiz() != null
                && answer.getQuestion().getQuiz().getLesson() != null
                && studentLearningAccessService.hasLessonAccess(student, answer.getQuestion().getQuiz().getLesson());
    }

    private static int overallCompletionPercent(List<LessonProgress> lessonProgressList) {
        if (lessonProgressList.isEmpty()) {
            return 0;
        }
        long completedLessons = lessonProgressList.stream().filter(LessonProgress::isCompleted).count();
        return (int) Math.round(completedLessons * 100.0 / lessonProgressList.size());
    }

    private static BigDecimal defaultMoney(BigDecimal amount) {
        if (amount == null) {
            return zeroMoney();
        }

        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroMoney() {
        return ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String studentDisplayName(QuizAttempt attempt) {
        return attempt.getStudent() != null && attempt.getStudent().getUser() != null
                ? attempt.getStudent().getUser().getDisplayName()
                : "Student";
    }
}
