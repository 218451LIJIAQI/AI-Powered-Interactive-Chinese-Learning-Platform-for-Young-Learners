package com.example.aichinese.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learning.BadgeSummaryResponse;
import com.example.aichinese.dto.learning.DailyStreakResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentProgressLessonResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.StudentQuizProgress;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.StudentQuizProgressRepository;
import com.example.aichinese.util.PaginationUtils;

/**
 * Tracks per-student progress, point/badge rewards, and the daily streak.
 *
 * <p>Two completion entry points are exposed:
 * <ul>
 *   <li>{@link #completeQuiz(StudentProfile, Quiz, int)} — for the
 *       teacher-owned, lesson-attached quiz flow. Updates lesson progress,
 *       awards quiz / lesson XP, fires streak + badge logic.</li>
 *   <li>{@link #completePlatformActivity(StudentProfile, String, Long, int, String)}
 *       — for admin-managed activities in the platform library
 *       (flashcards, matching, listening, tracing, drag-drop, story).
 *       These are decoupled from lessons; they only affect XP / streak.</li>
 * </ul>
 *
 * <p>Reward / progress / badge / streak read endpoints used by the student
 * dashboard are also implemented here.
 */
@Service
public class StudentLearningProgressService {

    private static final int QUIZ_COMPLETION_POINTS = 20;
    private static final int QUIZ_BONUS_POINTS = 10;
    private static final int LESSON_COMPLETION_POINTS = 25;

    private static final String SOURCE_QUIZ_COMPLETION = "QUIZ_COMPLETION";
    private static final String SOURCE_QUIZ_BONUS = "QUIZ_BONUS";
    private static final String SOURCE_LESSON_COMPLETION = "LESSON_COMPLETION";

    private final StudentQuizProgressRepository studentQuizProgressRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final DailyStreakRepository dailyStreakRepository;
    private final BadgeRepository badgeRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final StudentLearningAccessService studentLearningAccessService;

    public StudentLearningProgressService(
            StudentQuizProgressRepository studentQuizProgressRepository,
            LessonProgressRepository lessonProgressRepository,
            PointsTransactionRepository pointsTransactionRepository,
            DailyStreakRepository dailyStreakRepository,
            BadgeRepository badgeRepository,
            StudentBadgeRepository studentBadgeRepository,
            StudentProfileRepository studentProfileRepository,
            QuizRepository quizRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            StudentLearningAccessService studentLearningAccessService) {
        this.studentQuizProgressRepository = Objects.requireNonNull(studentQuizProgressRepository, "studentQuizProgressRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.pointsTransactionRepository = Objects.requireNonNull(pointsTransactionRepository, "pointsTransactionRepository");
        this.dailyStreakRepository = Objects.requireNonNull(dailyStreakRepository, "dailyStreakRepository");
        this.badgeRepository = Objects.requireNonNull(badgeRepository, "badgeRepository");
        this.studentBadgeRepository = Objects.requireNonNull(studentBadgeRepository, "studentBadgeRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
    }

    @Transactional
    public CompletionOutcome completeQuiz(StudentProfile student, Quiz quiz, int percentageScore) {
        StudentProfile lockedStudent = lockStudent(student);
        boolean firstCompletion = markQuizCompleted(
                lockedStudent,
                quiz,
                "Submitted quiz: " + quiz.getTitle());
        touchDailyStreak(lockedStudent);

        int earnedPoints = awardPointsIfFirst(
                lockedStudent,
                SOURCE_QUIZ_COMPLETION,
                quiz.getId(),
                QUIZ_COMPLETION_POINTS,
                "Completed quiz");

        if (percentageScore >= 80) {
            earnedPoints += awardPointsIfFirst(
                    lockedStudent,
                    SOURCE_QUIZ_BONUS,
                    quiz.getId(),
                    QUIZ_BONUS_POINTS,
                    "Achieved a high quiz score");
            awardBadgeIfNeeded(lockedStudent, "Quiz Star", "Scored at least 80% in a quiz.", "quiz-star");
        }

        int lessonCompletionPercent = updateLessonProgress(lockedStudent, quiz.getLesson());
        student.setTotalPoints(lockedStudent.getTotalPoints());
        return new CompletionOutcome(firstCompletion, earnedPoints, totalPoints(lockedStudent), lessonCompletionPercent);
    }

    @Transactional
    public PlatformActivityOutcome completePlatformActivity(
            StudentProfile student,
            String sourceType,
            Long sourceId,
            int xpReward,
            String reason) {
        StudentProfile lockedStudent = lockStudent(student);
        boolean alreadyAwarded = pointsTransactionRepository
                .existsByStudentIdAndSourceTypeAndSourceId(lockedStudent.getId(), sourceType, sourceId);
        int earnedPoints = alreadyAwarded
                ? 0
                : awardPointsIfFirst(lockedStudent, sourceType, sourceId, Math.max(0, xpReward), reason);
        touchDailyStreak(lockedStudent);
        student.setTotalPoints(lockedStudent.getTotalPoints());
        return new PlatformActivityOutcome(!alreadyAwarded, earnedPoints, totalPoints(lockedStudent));
    }

    @Transactional(readOnly = true)
    public boolean isPlatformActivityCompleted(StudentProfile student, String sourceType, Long sourceId) {
        return pointsTransactionRepository
                .existsByStudentIdAndSourceTypeAndSourceId(student.getId(), sourceType, sourceId);
    }

    @Transactional(readOnly = true)
    public int lessonCompletionPercent(StudentProfile student, Lesson lesson) {
        return lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId())
                .map(progress -> progress.getCompletionPercent() == null ? 0 : progress.getCompletionPercent())
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public StudentProgressSummaryResponse getProgressSummary(StudentProfile student) {
        List<LessonProgress> accessibleLessonProgress = lessonProgressRepository
                .findByStudentIdOrderByUpdatedAtDesc(student.getId()).stream()
                .filter(progress -> hasCurrentLessonProgressAccess(student, progress))
                .toList();
        int startedLessons = accessibleLessonProgress.size();
        int completedLessons = (int) accessibleLessonProgress.stream().filter(LessonProgress::isCompleted).count();
        int quizAverage = averageAccessibleQuizPercentage(student);

        List<String> weakAreas = extractWeakAreas(student);
        List<StudentProgressLessonResponse> recentLessons = accessibleLessonProgress.stream()
                .limit(10)
                .map(progress -> new StudentProgressLessonResponse(
                        progress.getLesson().getId(),
                        progress.getLesson().getTitle(),
                        progress.getCompletionPercent() == null ? 0 : progress.getCompletionPercent(),
                        progress.isCompleted(),
                        progress.getLastAccessedAt()))
                .toList();

        return new StudentProgressSummaryResponse(
                startedLessons,
                completedLessons,
                quizAverage,
                overallCompletionPercent(accessibleLessonProgress),
                weakAreas,
                latestAccessibleActivitySummary(accessibleLessonProgress),
                recentLessons);
    }

    @Transactional(readOnly = true)
    public RewardSummaryResponse getRewardSummary(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findByStudentId(student.getId()).orElse(null);
        List<BadgeSummaryResponse> badges = getBadges(student, 0, 10).items();

        List<PointsTransactionResponse> recentPoints = pointsTransactionRepository
                .findByStudentIdOrderByCreatedAtDesc(student.getId(), PageRequest.of(0, 10)).getContent().stream()
                .map(this::toPointsTransactionResponse)
                .toList();

        int totalPoints = totalPoints(student);
        int currentLevelNumber = Math.max(1, totalPoints / 100 + 1);
        int progressToNextLevelPercent = totalPoints % 100;

        return new RewardSummaryResponse(
                totalPoints,
                currentLevelNumber,
                progressToNextLevelPercent,
                new DailyStreakResponse(
                        streak != null && streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0,
                        streak != null && streak.getBestStreak() != null ? streak.getBestStreak() : 0,
                        streak != null ? streak.getLastLearningDate() : null),
                badges,
                recentPoints);
    }

    @Transactional(readOnly = true)
    public List<BadgeSummaryResponse> getBadges(StudentProfile student) {
        return getBadges(student, 0, 20).items();
    }

    @Transactional(readOnly = true)
    public PageResponse<BadgeSummaryResponse> getBadges(StudentProfile student, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<StudentBadge> badgePage = studentBadgeRepository.findByStudentIdOrderByAwardedAtDesc(
                student.getId(),
                PageRequest.of(safePage, safeSize));
        List<BadgeSummaryResponse> items = badgePage.getContent().stream()
                .map(this::toBadgeSummaryResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, badgePage.getTotalElements(), badgePage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<PointsTransactionResponse> getPointsHistory(StudentProfile student) {
        return getPointsHistory(student, 0, 20).items();
    }

    @Transactional(readOnly = true)
    public PageResponse<PointsTransactionResponse> getPointsHistory(StudentProfile student, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PointsTransaction> pointsPage = pointsTransactionRepository.findByStudentIdOrderByCreatedAtDesc(
                student.getId(),
                PageRequest.of(safePage, safeSize));
        List<PointsTransactionResponse> items = pointsPage.getContent().stream()
                .map(this::toPointsTransactionResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, pointsPage.getTotalElements(), pointsPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DailyStreakResponse getStreak(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findByStudentId(student.getId()).orElse(null);
        return new DailyStreakResponse(
                streak != null && streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0,
                streak != null && streak.getBestStreak() != null ? streak.getBestStreak() : 0,
                streak != null ? streak.getLastLearningDate() : null);
    }

    // ===================================================================
    // Internal helpers
    // ===================================================================

    private boolean markQuizCompleted(StudentProfile student, Quiz quiz, String summary) {
        return recordQuizProgress(student, quiz, true, summary);
    }

    private StudentProfile lockStudent(StudentProfile student) {
        return studentProfileRepository.findLockedById(student.getId()).orElse(student);
    }

    private boolean recordQuizProgress(
            StudentProfile student,
            Quiz quiz,
            boolean completed,
            String summary) {
        StudentQuizProgress progress = studentQuizProgressRepository
                .findByStudentIdAndQuizId(student.getId(), quiz.getId())
                .orElseGet(StudentQuizProgress::new);
        boolean firstCompletion = !progress.isCompleted() && completed;
        LocalDateTime now = LocalDateTime.now();

        if (progress.getId() == null) {
            progress.setStudent(student);
            progress.setLesson(quiz.getLesson());
            progress.setQuizId(quiz.getId());
            progress.setStartedAt(now);
        }

        progress.setAttemptCount((progress.getAttemptCount() == null ? 0 : progress.getAttemptCount()) + 1);
        progress.setLastInteractedAt(now);
        progress.setCompletionSummary(summary);
        if (completed) {
            progress.setCompleted(true);
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(now);
            }
        }
        studentQuizProgressRepository.save(progress);
        return firstCompletion;
    }

    private void touchDailyStreak(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findLockedByStudentId(student.getId())
                .orElseGet(DailyStreak::new);
        LocalDate today = LocalDate.now();

        if (streak.getId() == null) {
            streak.setStudent(student);
            streak.setCurrentStreak(1);
            streak.setBestStreak(1);
            streak.setLastLearningDate(today);
            dailyStreakRepository.save(streak);
            awardBadgeIfNeeded(student, "First Steps", "Completed a learning activity for the first time.", "first-steps");
            return;
        }

        LocalDate lastDate = streak.getLastLearningDate();
        if (Objects.equals(lastDate, today)) {
            return;
        }

        if (lastDate != null && lastDate.plusDays(1).equals(today)) {
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        } else {
            streak.setCurrentStreak(1);
        }

        streak.setBestStreak(Math.max(
                streak.getBestStreak() == null ? 0 : streak.getBestStreak(),
                streak.getCurrentStreak()));
        streak.setLastLearningDate(today);
        dailyStreakRepository.save(streak);

        if (streak.getCurrentStreak() >= 3) {
            awardBadgeIfNeeded(student, "Streak Explorer", "Reached a three-day learning streak.", "streak-explorer");
        }
    }

    private int updateLessonProgress(StudentProfile student, Lesson lesson) {
        int totalActivities = (int) quizRepository.countByLessonId(lesson.getId());
        long completedActivities = studentQuizProgressRepository.findByStudentIdAndLessonIdOrderByUpdatedAtDesc(
                student.getId(),
                lesson.getId()).stream()
                .filter(StudentQuizProgress::isCompleted)
                .count();

        int percent = totalActivities == 0 ? 0 : (int) Math.round((completedActivities * 100.0) / totalActivities);
        boolean completed = totalActivities > 0 && completedActivities >= totalActivities;

        LessonProgress lessonProgress = lessonProgressRepository.findLockedByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElseGet(LessonProgress::new);
        boolean firstLessonCompletion = !lessonProgress.isCompleted() && completed;

        if (lessonProgress.getId() == null) {
            lessonProgress.setStudent(student);
            lessonProgress.setLesson(lesson);
        }
        lessonProgress.setCompletionPercent(percent);
        lessonProgress.setCompleted(completed);
        lessonProgress.setLastAccessedAt(LocalDateTime.now());
        lessonProgressRepository.save(lessonProgress);

        if (firstLessonCompletion) {
            awardPointsIfFirst(
                    student,
                    SOURCE_LESSON_COMPLETION,
                    lesson.getId(),
                    LESSON_COMPLETION_POINTS,
                    "Completed a lesson");
            awardBadgeIfNeeded(student, "Lesson Finisher", "Completed a lesson successfully.", "lesson-finisher");
        }

        return percent;
    }

    private int awardPointsIfFirst(StudentProfile student, String sourceType, Long sourceId, int points, String reason) {
        StudentProfile lockedStudent = studentProfileRepository.findLockedById(student.getId()).orElse(student);
        if (pointsTransactionRepository.existsByStudentIdAndSourceTypeAndSourceId(student.getId(), sourceType, sourceId)) {
            return 0;
        }

        PointsTransaction transaction = new PointsTransaction();
        transaction.setStudent(lockedStudent);
        transaction.setPointsDelta(points);
        transaction.setReason(reason);
        transaction.setSourceType(sourceType);
        transaction.setSourceId(sourceId);
        pointsTransactionRepository.save(transaction);

        lockedStudent.setTotalPoints(totalPoints(lockedStudent) + points);
        student.setTotalPoints(lockedStudent.getTotalPoints());
        if (lockedStudent.getTotalPoints() >= 50) {
            awardBadgeIfNeeded(lockedStudent, "Point Starter", "Collected at least 50 learning points.", "point-starter");
        }
        return points;
    }

    private void awardBadgeIfNeeded(StudentProfile student, String name, String description, String iconKey) {
        Badge badge = badgeRepository.findByName(name)
                .orElseGet(() -> {
                    Badge created = new Badge();
                    created.setName(name);
                    created.setDescription(description);
                    created.setIconKey(iconKey);
                    created.setActive(true);
                    return badgeRepository.save(created);
                });

        if (studentBadgeRepository.existsByStudentIdAndBadgeId(student.getId(), badge.getId())) {
            return;
        }

        StudentBadge studentBadge = new StudentBadge();
        studentBadge.setStudent(student);
        studentBadge.setBadge(badge);
        studentBadge.setAwardedAt(LocalDateTime.now());
        studentBadgeRepository.save(studentBadge);
    }

    private int totalPoints(StudentProfile student) {
        return student.getTotalPoints() == null ? 0 : student.getTotalPoints();
    }

    private BadgeSummaryResponse toBadgeSummaryResponse(StudentBadge badge) {
        return new BadgeSummaryResponse(
                badge.getBadge().getId(),
                badge.getBadge().getName(),
                badge.getBadge().getDescription(),
                badge.getBadge().getIconKey(),
                badge.getAwardedAt());
    }

    private PointsTransactionResponse toPointsTransactionResponse(PointsTransaction transaction) {
        return new PointsTransactionResponse(
                transaction.getId(),
                transaction.getPointsDelta(),
                transaction.getReason(),
                transaction.getSourceType(),
                transaction.getSourceId(),
                transaction.getCreatedAt());
    }

    private int round(double value) {
        return (int) Math.round(value);
    }

    private boolean hasCurrentLessonProgressAccess(StudentProfile student, LessonProgress progress) {
        return progress.getLesson() == null || studentLearningAccessService.hasLessonAccess(student, progress.getLesson());
    }

    private boolean hasCurrentQuizAttemptAccess(StudentProfile student, QuizAttempt attempt) {
        return attempt.getQuiz() == null
                || attempt.getQuiz().getLesson() == null
                || studentLearningAccessService.hasLessonAccess(student, attempt.getQuiz().getLesson());
    }

    private int averageAccessibleQuizPercentage(StudentProfile student) {
        List<Integer> percentages = quizAttemptRepository.findByStudentIdOrderByCompletedAtDescCreatedAtDesc(student.getId()).stream()
                .filter(attempt -> hasCurrentQuizAttemptAccess(student, attempt))
                .filter(attempt -> attempt.isSubmitted()
                        && attempt.getScore() != null
                        && attempt.getMaxScore() != null
                        && attempt.getMaxScore() > 0)
                .map(attempt -> round((attempt.getScore() * 100.0) / attempt.getMaxScore()))
                .toList();
        if (percentages.isEmpty()) {
            return 0;
        }
        return round(percentages.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private int overallCompletionPercent(List<LessonProgress> lessonProgressList) {
        if (lessonProgressList.isEmpty()) {
            return 0;
        }
        long completedLessons = lessonProgressList.stream().filter(LessonProgress::isCompleted).count();
        return round((completedLessons * 100.0) / lessonProgressList.size());
    }

    private String latestAccessibleActivitySummary(List<LessonProgress> lessonProgressList) {
        return lessonProgressList.stream()
                .findFirst()
                .filter(progress -> progress.getLesson() != null)
                .map(progress -> progress.getLesson().getTitle())
                .orElse(null);
    }

    private List<String> extractWeakAreas(StudentProfile student) {
        return quizAttemptAnswerRepository.findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(student.getId()).stream()
                .filter(answer -> hasCurrentWrongAnswerAccess(student, answer))
                .map(answer -> answer.getQuestion() == null ? null : answer.getQuestion().getQuestionType())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private boolean hasCurrentWrongAnswerAccess(StudentProfile student, QuizAttemptAnswer answer) {
        return answer.getQuestion() != null
                && answer.getQuestion().getQuiz() != null
                && answer.getQuestion().getQuiz().getLesson() != null
                && studentLearningAccessService.hasLessonAccess(student, answer.getQuestion().getQuiz().getLesson());
    }

    public record CompletionOutcome(
            boolean firstCompletion,
            int earnedPoints,
            int totalPoints,
            int lessonCompletionPercent) {
    }

    /**
     * Outcome of {@link #completePlatformActivity}. Distinct from
     * {@link CompletionOutcome} because platform-managed activities are
     * decoupled from lessons, so {@code lessonCompletionPercent} has no
     * meaning here.
     */
    public record PlatformActivityOutcome(
            boolean firstCompletion,
            int earnedPoints,
            int totalPoints) {
    }
}
