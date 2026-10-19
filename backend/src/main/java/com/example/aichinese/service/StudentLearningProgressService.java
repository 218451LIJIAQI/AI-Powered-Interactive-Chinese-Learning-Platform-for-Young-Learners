package com.example.aichinese.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learning.BadgeSummaryResponse;
import com.example.aichinese.dto.learning.DailyStreakResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonProgressResponse;
import com.example.aichinese.dto.learning.StudentProgressLessonResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LessonMastery;
import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.RecordedLessonVideo;
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
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.StudentQuizProgressRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.util.PaginationUtils;

/**
 * Lesson progress, mastery (D25), rewards and streaks.
 *
 * <p>Lesson completion: a lesson with published quizzes completes when every published quiz has
 * been submitted; a lesson without quizzes completes when its content was viewed and all of its
 * active recorded videos are completed. Archived, draft or in-review quizzes never count.</p>
 */
@Service
public class StudentLearningProgressService {

    private static final int QUIZ_COMPLETION_POINTS = 20;
    private static final int QUIZ_BONUS_POINTS = 10;
    private static final int LESSON_COMPLETION_POINTS = 25;
    private static final int HIGH_QUIZ_SCORE_PERCENT = 80;
    private static final int POINT_STARTER_THRESHOLD = RewardRules.POINT_STARTER_THRESHOLD;
    private static final int STREAK_EXPLORER_DAYS = RewardRules.STREAK_EXPLORER_DAYS;
    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final int RECENT_REWARD_LIMIT = 10;
    private static final int WEAK_AREA_LIMIT = 5;
    /** Lesson titles read per query; more pages are read only when blank or duplicate titles were skipped. */
    private static final int WEAK_AREA_TITLE_BATCH = 20;

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
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final VideoProgressRepository videoProgressRepository;

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
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            VideoProgressRepository videoProgressRepository) {
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
        this.recordedLessonVideoRepository = Objects.requireNonNull(recordedLessonVideoRepository, "recordedLessonVideoRepository");
        this.videoProgressRepository = Objects.requireNonNull(videoProgressRepository, "videoProgressRepository");
    }

    @Transactional
    public CompletionOutcome completeQuiz(StudentProfile student, Quiz quiz, int percentageScore) {
        StudentProfile lockedStudent = lockStudent(student);
        recordQuizCompletion(
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

        if (percentageScore >= HIGH_QUIZ_SCORE_PERCENT) {
            earnedPoints += awardPointsIfFirst(
                    lockedStudent,
                    SOURCE_QUIZ_BONUS,
                    quiz.getId(),
                    QUIZ_BONUS_POINTS,
                    "Achieved a high quiz score");
            awardBadgeIfNeeded(lockedStudent, RewardBadge.QUIZ_STAR);
        }

        updateLessonProgress(lockedStudent, quiz.getLesson(), false);
        student.setTotalPoints(lockedStudent.getTotalPoints());
        return new CompletionOutcome(earnedPoints, totalPoints(lockedStudent));
    }

    /**
     * Records that the student viewed the lesson content (lesson page shown) and recomputes the
     * lesson progress; lessons without quizzes may complete here.
     */
    @Transactional
    public StudentLessonProgressResponse recordLessonContentViewed(StudentProfile student, Lesson lesson) {
        StudentProfile lockedStudent = lockStudent(student);
        LessonProgress progress = updateLessonProgress(lockedStudent, lesson, true);
        student.setTotalPoints(lockedStudent.getTotalPoints());
        Integer bestQuizPercentage = bestQuizPercentageForLesson(student, lesson);
        return new StudentLessonProgressResponse(
                lesson.getId(),
                count(progress.getCompletionPercent()),
                progress.isCompleted(),
                LessonMasteryCalculator.mastery(progress, bestQuizPercentage).name(),
                bestQuizPercentage,
                progress.getContentViewedAt() != null);
    }

    /** A recorded video of the lesson was completed: recompute lesson progress (completion rule above). */
    @Transactional
    public void recordLessonVideoCompleted(StudentProfile student, Lesson lesson) {
        if (lesson == null) {
            return;
        }
        StudentProfile lockedStudent = lockStudent(student);
        updateLessonProgress(lockedStudent, lesson, false);
        student.setTotalPoints(lockedStudent.getTotalPoints());
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

    /** Current progress and mastery of one lesson for the lesson page. */
    @Transactional(readOnly = true)
    public LessonProgressSnapshot lessonSnapshot(StudentProfile student, Lesson lesson) {
        LessonProgress progress = lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElse(null);
        Integer bestQuizPercentage = bestQuizPercentageForLesson(student, lesson);
        return new LessonProgressSnapshot(
                progress == null ? 0 : count(progress.getCompletionPercent()),
                progress != null && progress.isCompleted(),
                progress != null && progress.getContentViewedAt() != null,
                LessonMasteryCalculator.mastery(progress, bestQuizPercentage),
                bestQuizPercentage);
    }

    @Transactional(readOnly = true)
    public StudentProgressSummaryResponse getProgressSummary(StudentProfile student) {
        List<LessonProgress> accessibleLessonProgress = lessonProgressRepository
                .findByStudentIdOrderByUpdatedAtDesc(student.getId()).stream()
                .filter(progress -> progress.getLesson() != null)
                .toList();
        List<QuizAttempt> attempts = quizAttemptRepository.findByStudentIdOrderByCompletedAtDescCreatedAtDesc(student.getId());
        Map<Long, Integer> bestQuizByLesson = LessonMasteryCalculator.bestQuizPercentageByLesson(attempts);
        int startedLessons = accessibleLessonProgress.size();
        int completedLessons = (int) accessibleLessonProgress.stream().filter(LessonProgress::isCompleted).count();
        int quizAverage = averageQuizPercentage(attempts);

        List<String> weakAreas = extractWeakAreas(student);
        List<StudentProgressLessonResponse> lessons = accessibleLessonProgress.stream()
                .map(progress -> toStudentProgressLessonResponse(progress, bestQuizByLesson.get(progress.getLesson().getId())))
                .toList();

        return new StudentProgressSummaryResponse(
                startedLessons,
                completedLessons,
                quizAverage,
                overallCompletionPercent(accessibleLessonProgress),
                weakAreas,
                latestAccessibleActivitySummary(accessibleLessonProgress),
                lessons.stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
                lessons);
    }

    @Transactional(readOnly = true)
    public RewardSummaryResponse getRewardSummary(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findByStudentId(student.getId()).orElse(null);
        List<BadgeSummaryResponse> badges = getBadges(student, 0, RECENT_REWARD_LIMIT).items();

        List<PointsTransactionResponse> recentPoints = pointsTransactionRepository
                .findByStudentIdOrderByCreatedAtDesc(student.getId(), PageRequest.of(0, RECENT_REWARD_LIMIT))
                .getContent().stream()
                .map(StudentLearningProgressService::toPointsTransactionResponse)
                .toList();

        int totalPoints = totalPoints(student);
        int currentLevelNumber = RewardRules.levelFor(totalPoints);
        int progressToNextLevelPercent = RewardRules.progressToNextLevelPercent(totalPoints);

        return new RewardSummaryResponse(
                totalPoints,
                currentLevelNumber,
                progressToNextLevelPercent,
                toDailyStreakResponse(streak),
                badges,
                recentPoints);
    }

    @Transactional(readOnly = true)
    public PageResponse<BadgeSummaryResponse> getBadges(StudentProfile student, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<StudentBadge> badgePage = studentBadgeRepository.findByStudentIdOrderByAwardedAtDesc(
                student.getId(),
                PageRequest.of(safePage, safeSize));
        List<BadgeSummaryResponse> items = badgePage.getContent().stream()
                .map(StudentLearningProgressService::toBadgeSummaryResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, badgePage.getTotalElements(), badgePage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<PointsTransactionResponse> getPointsHistory(StudentProfile student, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PointsTransaction> pointsPage = pointsTransactionRepository.findByStudentIdOrderByCreatedAtDesc(
                student.getId(),
                PageRequest.of(safePage, safeSize));
        List<PointsTransactionResponse> items = pointsPage.getContent().stream()
                .map(StudentLearningProgressService::toPointsTransactionResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, pointsPage.getTotalElements(), pointsPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DailyStreakResponse getStreak(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findByStudentId(student.getId()).orElse(null);
        return toDailyStreakResponse(streak);
    }

    private StudentProfile lockStudent(StudentProfile student) {
        return studentProfileRepository.findLockedById(student.getId()).orElse(student);
    }

    private void recordQuizCompletion(
            StudentProfile student,
            Quiz quiz,
            String summary) {
        StudentQuizProgress progress = studentQuizProgressRepository
                .findByStudentIdAndQuizId(student.getId(), quiz.getId())
                .orElseGet(StudentQuizProgress::new);
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
        progress.setCompleted(true);
        if (progress.getCompletedAt() == null) {
            progress.setCompletedAt(now);
        }
        studentQuizProgressRepository.save(progress);
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
            awardBadgeIfNeeded(student, RewardBadge.FIRST_STEPS);
            return;
        }

        LocalDate lastDate = streak.getLastLearningDate();
        if (Objects.equals(lastDate, today)) {
            return;
        }

        streak.setCurrentStreak(RewardRules.nextStreak(lastDate, count(streak.getCurrentStreak()), today));

        streak.setBestStreak(Math.max(
                count(streak.getBestStreak()),
                streak.getCurrentStreak()));
        streak.setLastLearningDate(today);
        dailyStreakRepository.save(streak);

        if (streak.getCurrentStreak() >= STREAK_EXPLORER_DAYS) {
            awardBadgeIfNeeded(student, RewardBadge.STREAK_EXPLORER);
        }
    }

    private LessonProgress updateLessonProgress(StudentProfile student, Lesson lesson, boolean contentViewedNow) {
        LessonProgress lessonProgress = lessonProgressRepository.findLockedByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElseGet(LessonProgress::new);
        if (lessonProgress.getId() == null) {
            lessonProgress.setStudent(student);
            lessonProgress.setLesson(lesson);
        }
        LocalDateTime now = LocalDateTime.now();
        if (contentViewedNow && lessonProgress.getContentViewedAt() == null) {
            lessonProgress.setContentViewedAt(now);
        }

        LessonCompletion completionState = computeLessonCompletion(student, lesson, lessonProgress.getContentViewedAt() != null);
        // Completion is sticky: once a lesson was completed it stays completed (learner progress is kept, D24).
        boolean firstLessonCompletion = !lessonProgress.isCompleted() && completionState.completed();
        boolean completed = lessonProgress.isCompleted() || completionState.completed();
        lessonProgress.setCompletionPercent(completed ? 100 : completionState.percent());
        lessonProgress.setCompleted(completed);
        lessonProgress.setLastAccessedAt(now);
        LessonProgress saved = lessonProgressRepository.save(lessonProgress);

        if (firstLessonCompletion) {
            awardPointsIfFirst(
                    student,
                    SOURCE_LESSON_COMPLETION,
                    lesson.getId(),
                    LESSON_COMPLETION_POINTS,
                    "Completed a lesson");
            awardBadgeIfNeeded(student, RewardBadge.LESSON_FINISHER);
        }
        return saved;
    }

    private LessonCompletion computeLessonCompletion(StudentProfile student, Lesson lesson, boolean contentViewed) {
        // Only quizzes students can actually take count (archived, draft or in-review quizzes never block completion).
        int publishedQuizzes = (int) quizRepository.countByLessonIdAndContentStatus(lesson.getId(), ContentStatus.PUBLISHED);
        if (publishedQuizzes > 0) {
            long completedQuizzes = studentQuizProgressRepository.findByStudentIdAndLessonIdOrderByUpdatedAtDesc(
                    student.getId(),
                    lesson.getId()).stream()
                    .filter(StudentQuizProgress::isCompleted)
                    .filter(progress -> progress.getQuiz() == null
                            || progress.getQuiz().getContentStatus() == ContentStatus.PUBLISHED)
                    .count();
            int percent = round(Math.min(completedQuizzes, publishedQuizzes) * 100.0 / publishedQuizzes);
            return new LessonCompletion(percent, completedQuizzes >= publishedQuizzes);
        }

        List<Long> videoIds = recordedLessonVideoRepository.findActiveRelatedToLesson(lesson.getId()).stream()
                .filter(video -> video.getCoursePackage() == null
                        || ContentVisibilityPolicy.isPackageVisible(video.getCoursePackage()))
                .map(RecordedLessonVideo::getId)
                .toList();
        long completedVideos = videoIds.isEmpty()
                ? 0
                : videoProgressRepository.countByStudentIdAndRecordedVideoIdInAndCompletedTrue(student.getId(), videoIds);
        int total = 1 + videoIds.size();
        long done = (contentViewed ? 1 : 0) + Math.min(completedVideos, videoIds.size());
        return new LessonCompletion(round(done * 100.0 / total), done >= total);
    }

    private Integer bestQuizPercentageForLesson(StudentProfile student, Lesson lesson) {
        List<QuizAttempt> lessonAttempts =
                quizAttemptRepository.findByStudentIdAndQuizLessonId(student.getId(), lesson.getId());
        return LessonMasteryCalculator.bestQuizPercentageByLesson(lessonAttempts).get(lesson.getId());
    }

    private int awardPointsIfFirst(StudentProfile student, String sourceType, Long sourceId, int points, String reason) {
        if (pointsTransactionRepository.existsByStudentIdAndSourceTypeAndSourceId(student.getId(), sourceType, sourceId)) {
            return 0;
        }

        PointsTransaction transaction = new PointsTransaction();
        transaction.setStudent(student);
        transaction.setPointsDelta(points);
        transaction.setReason(reason);
        transaction.setSourceType(sourceType);
        transaction.setSourceId(sourceId);
        pointsTransactionRepository.save(transaction);

        student.setTotalPoints(totalPoints(student) + points);
        if (totalPoints(student) >= POINT_STARTER_THRESHOLD) {
            awardBadgeIfNeeded(student, RewardBadge.POINT_STARTER);
        }
        return points;
    }

    private void awardBadgeIfNeeded(StudentProfile student, RewardBadge rewardBadge) {
        // The badge row is seeded by DataSeeder (and V6 for existing databases); creating it here
        // is only a fallback so an award never fails on a database that was seeded differently.
        Badge badge = badgeRepository.findByName(rewardBadge.badgeName())
                .orElseGet(() -> {
                    Badge created = new Badge();
                    created.setName(rewardBadge.badgeName());
                    created.setDescription(rewardBadge.description());
                    created.setIconKey(rewardBadge.iconKey());
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

    private static int totalPoints(StudentProfile student) {
        return student.getTotalPoints() == null ? 0 : student.getTotalPoints();
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    private static StudentProgressLessonResponse toStudentProgressLessonResponse(
            LessonProgress progress,
            Integer bestQuizPercentage) {
        Lesson lesson = progress.getLesson();
        LessonMastery mastery = LessonMasteryCalculator.mastery(progress, bestQuizPercentage);
        return new StudentProgressLessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                count(progress.getCompletionPercent()),
                progress.isCompleted(),
                progress.getLastAccessedAt(),
                mastery.name(),
                bestQuizPercentage);
    }

    private static BadgeSummaryResponse toBadgeSummaryResponse(StudentBadge badge) {
        Badge badgeDetails = badge.getBadge();
        return new BadgeSummaryResponse(
                badgeDetails.getId(),
                badgeDetails.getName(),
                badgeDetails.getDescription(),
                badgeDetails.getIconKey(),
                badge.getAwardedAt());
    }

    private static PointsTransactionResponse toPointsTransactionResponse(PointsTransaction transaction) {
        return new PointsTransactionResponse(
                transaction.getId(),
                transaction.getPointsDelta(),
                transaction.getReason(),
                transaction.getSourceType(),
                transaction.getSourceId(),
                transaction.getCreatedAt());
    }

    private static DailyStreakResponse toDailyStreakResponse(DailyStreak streak) {
        return new DailyStreakResponse(
                streak == null ? 0 : count(streak.getCurrentStreak()),
                streak == null ? 0 : count(streak.getBestStreak()),
                streak != null ? streak.getLastLearningDate() : null);
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static int averageQuizPercentage(List<QuizAttempt> attempts) {
        List<Integer> percentages = attempts.stream()
                .map(LessonMasteryCalculator::attemptPercentage)
                .filter(Objects::nonNull)
                .toList();
        if (percentages.isEmpty()) {
            return 0;
        }
        return round(percentages.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static int overallCompletionPercent(List<LessonProgress> lessonProgressList) {
        if (lessonProgressList.isEmpty()) {
            return 0;
        }
        long completedLessons = lessonProgressList.stream().filter(LessonProgress::isCompleted).count();
        return round(completedLessons * 100.0 / lessonProgressList.size());
    }

    private static String latestAccessibleActivitySummary(List<LessonProgress> lessonProgressList) {
        return lessonProgressList.stream()
                .findFirst()
                .map(progress -> progress.getLesson().getTitle())
                .orElse(null);
    }

    /** Weak areas = lesson titles (topics) of the student's most recent wrong quiz answers. */
    private List<String> extractWeakAreas(StudentProfile student) {
        Set<String> topics = new LinkedHashSet<>();
        List<String> titles;
        int page = 0;
        do {
            titles = quizAttemptAnswerRepository.findRecentWrongAnswerLessonTitles(
                    student.getId(), PageRequest.of(page++, WEAK_AREA_TITLE_BATCH));
            for (String title : titles) {
                String topic = weakAreaTopic(title);
                if (topic != null) {
                    topics.add(topic);
                }
                if (topics.size() >= WEAK_AREA_LIMIT) {
                    return List.copyOf(topics);
                }
            }
        } while (titles.size() == WEAK_AREA_TITLE_BATCH);
        return List.copyOf(topics);
    }

    /** Topic label of a wrong answer: the title of the lesson the quiz belongs to. */
    static String weakAreaTopic(QuizAttemptAnswer answer) {
        if (answer == null || answer.getQuestion() == null || answer.getQuestion().getQuiz() == null) {
            return null;
        }
        Lesson lesson = answer.getQuestion().getQuiz().getLesson();
        return lesson == null ? null : weakAreaTopic(lesson.getTitle());
    }

    private static String weakAreaTopic(String lessonTitle) {
        if (lessonTitle == null || lessonTitle.isBlank()) {
            return null;
        }
        return lessonTitle.strip();
    }

    private record LessonCompletion(int percent, boolean completed) {
    }

    /** Progress and mastery of one lesson. */
    public record LessonProgressSnapshot(
            int completionPercent,
            boolean completed,
            boolean contentViewed,
            LessonMastery mastery,
            Integer bestQuizPercentage) {
    }

    public record CompletionOutcome(
            int earnedPoints,
            int totalPoints) {
    }

    public record PlatformActivityOutcome(
            boolean firstCompletion,
            int earnedPoints,
            int totalPoints) {
    }
}
