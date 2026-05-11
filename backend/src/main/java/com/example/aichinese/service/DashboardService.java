package com.example.aichinese.service;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.dashboard.AdminDashboardResponse;
import com.example.aichinese.dto.dashboard.StudentDashboardResponse;
import com.example.aichinese.dto.dashboard.TeacherDashboardResponse;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.StudentProgress;
import com.example.aichinese.entity.TeacherProfile;
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
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RefundRequestRepository;
import com.example.aichinese.repository.ReportRepository;
import com.example.aichinese.repository.SavedItemRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.StudentProgressRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DashboardService {

    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final DailyStreakRepository dailyStreakRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final SavedItemRepository savedItemRepository;
    private final NotificationRepository notificationRepository;
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
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardService(
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            StudentProgressRepository studentProgressRepository,
            DailyStreakRepository dailyStreakRepository,
            StudentBadgeRepository studentBadgeRepository,
            QuizAttemptRepository quizAttemptRepository,
            LessonProgressRepository lessonProgressRepository,
            SavedItemRepository savedItemRepository,
            NotificationRepository notificationRepository,
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
            ReportRepository reportRepository,
            AuditLogRepository auditLogRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.dailyStreakRepository = dailyStreakRepository;
        this.studentBadgeRepository = studentBadgeRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.savedItemRepository = savedItemRepository;
        this.notificationRepository = notificationRepository;
        this.lessonRepository = lessonRepository;
        this.teacherEarningRepository = teacherEarningRepository;
        this.userRepository = userRepository;
        this.learningModuleRepository = learningModuleRepository;
        this.quizRepository = quizRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.flashcardRepository = flashcardRepository;
        this.listeningExerciseRepository = listeningExerciseRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.payoutRequestRepository = payoutRequestRepository;
        this.reportRepository = reportRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse getStudentDashboard(SecurityUserDetails currentUser) {
        StudentProfile studentProfile = studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));

        StudentProgress latestProgress = studentProgressRepository.findFirstByStudentIdOrderByUpdatedAtDesc(studentProfile.getId())
                .orElse(null);

        String currentLevel = studentProfile.getCurrentLevel() != null
                ? studentProfile.getCurrentLevel().getName()
                : "Beginner";
        int progressPercent = latestProgress != null && latestProgress.getCompletionPercent() != null
                ? latestProgress.getCompletionPercent()
                : 0;
        int dailyStreak = dailyStreakRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentProfile.getId())
                .map(streak -> streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0)
                .orElse(0);
        long badgeCount = studentBadgeRepository.countByStudentId(studentProfile.getId());

        List<StudentDashboardResponse.RecentQuizResult> recentQuizResults = quizAttemptRepository
                .findTop5ByStudentIdOrderByCreatedAtDesc(studentProfile.getId()).stream()
                .map(attempt -> new StudentDashboardResponse.RecentQuizResult(
                        attempt.getId(),
                        attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : "Practice Quiz",
                        attempt.getScore(),
                        attempt.getMaxScore(),
                        attempt.getCompletedAt()))
                .toList();

        List<String> weakAreas = extractWeakAreas(latestProgress);

        List<StudentDashboardResponse.LessonProgressSummary> unfinishedLessons = lessonProgressRepository
                .findTop5ByStudentIdAndCompletedFalseOrderByUpdatedAtDesc(studentProfile.getId()).stream()
                .map(progress -> new StudentDashboardResponse.LessonProgressSummary(
                        progress.getLesson().getId(),
                        progress.getLesson().getTitle(),
                        progress.getCompletionPercent(),
                        progress.getLastAccessedAt()))
                .toList();

        List<StudentDashboardResponse.SavedItemSummary> savedItems = savedItemRepository
                .findTop5ByStudentIdOrderByCreatedAtDesc(studentProfile.getId()).stream()
                .map(savedItem -> new StudentDashboardResponse.SavedItemSummary(
                        savedItem.getId(),
                        savedItem.getItemType(),
                        savedItem.getItemId(),
                        savedItem.getNote(),
                        savedItem.getCreatedAt()))
                .toList();

        List<StudentDashboardResponse.NotificationSummary> notifications = mapStudentNotifications(
                notificationRepository.findTop5ByUserIdOrderByCreatedAtDesc(currentUser.getUserId()));

        return new StudentDashboardResponse(
                currentUser.getUser().getDisplayName(),
                currentLevel,
                progressPercent,
                dailyStreak,
                studentProfile.getTotalPoints() != null ? studentProfile.getTotalPoints() : 0,
                badgeCount,
                recentQuizResults,
                weakAreas,
                unfinishedLessons,
                List.of(),
                savedItems,
                notifications,
                buildStudentRecommendation(weakAreas, progressPercent));
    }

    @Transactional(readOnly = true)
    public TeacherDashboardResponse getTeacherDashboard(SecurityUserDetails currentUser) {
        TeacherProfile teacherProfile = teacherProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));

        BigDecimal grossAmount = defaultMoney(teacherEarningRepository.sumGrossAmountByTeacherId(teacherProfile.getId()));
        BigDecimal netAmount = defaultMoney(teacherEarningRepository.sumNetAmountByTeacherId(teacherProfile.getId()));
        BigDecimal averageQuizScore = scaleDecimal(quizAttemptRepository.findAverageScoreByTeacherId(teacherProfile.getId()));
        BigDecimal ratingAverage = teacherProfile.getAverageRating() != null
                ? teacherProfile.getAverageRating().setScale(2, RoundingMode.HALF_UP)
                : ZERO.setScale(2, RoundingMode.HALF_UP);

        List<TeacherDashboardResponse.RecentQuizResult> recentQuizResults = quizAttemptRepository
                .findTop5ByQuizTeacherIdOrderByCreatedAtDesc(teacherProfile.getId()).stream()
                .map(attempt -> new TeacherDashboardResponse.RecentQuizResult(
                        attempt.getId(),
                        attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : "Practice Quiz",
                        attempt.getStudent() != null && attempt.getStudent().getUser() != null
                                ? attempt.getStudent().getUser().getDisplayName()
                                : "Student",
                        attempt.getScore(),
                        attempt.getMaxScore(),
                        attempt.getCompletedAt()))
                .toList();

        List<TeacherDashboardResponse.NotificationSummary> notifications = notificationRepository
                .findTop5ByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(this::mapTeacherNotification)
                .toList();

        return new TeacherDashboardResponse(
                currentUser.getUser().getDisplayName(),
                teacherProfile.getTeacherStatus(),
                lessonRepository.countByTeacherId(teacherProfile.getId()),
                lessonProgressRepository.countDistinctStudentsByTeacherId(teacherProfile.getId()),
                averageQuizScore,
                ratingAverage,
                new TeacherDashboardResponse.EarningsSummary(
                        grossAmount,
                        netAmount,
                        netAmount,
                        ZERO.setScale(2, RoundingMode.HALF_UP)),
                List.of(),
                recentQuizResults,
                List.of(),
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

        List<AdminDashboardResponse.RecentReportSummary> recentReports = reportRepository.findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(report -> new AdminDashboardResponse.RecentReportSummary(
                        report.getId(),
                        report.getTargetType(),
                        report.getReportStatus().name(),
                        report.getCreatedAt()))
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
                currentUser.getUser().getDisplayName(),
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
                        defaultMoney(paymentRecordRepository.sumAmountByPaymentStatus(PaymentStatus.PAID))),
                refundRequestRepository.countByRefundStatus(RefundStatus.PENDING),
                payoutRequestRepository.countByPayoutStatus(PayoutStatus.PENDING),
                recentReports,
                recentAuditLogs);
    }

    private List<StudentDashboardResponse.NotificationSummary> mapStudentNotifications(List<Notification> notifications) {
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

    private TeacherDashboardResponse.NotificationSummary mapTeacherNotification(Notification notification) {
        return new TeacherDashboardResponse.NotificationSummary(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationStatus().name(),
                notification.getTargetPath(),
                notification.getCreatedAt());
    }

    private List<String> extractWeakAreas(StudentProgress latestProgress) {
        if (latestProgress == null || latestProgress.getWeakAreas() == null || latestProgress.getWeakAreas().isBlank()) {
            return List.of();
        }

        return List.of(latestProgress.getWeakAreas().split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String buildStudentRecommendation(List<String> weakAreas, int progressPercent) {
        if (!weakAreas.isEmpty()) {
            return "Focus on " + weakAreas.get(0) + " with short daily practice before moving to new topics.";
        }

        if (progressPercent == 0) {
            return "Start with beginner vocabulary, pinyin sounds, and one short lesson to build your learning streak.";
        }

        return "Keep going with your next beginner lesson and review one previous activity to strengthen retention.";
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
}
