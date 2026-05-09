package com.example.aichinese.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learning.BadgeSummaryResponse;
import com.example.aichinese.dto.learning.DailyStreakResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentProgressLessonResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentActivityProgress;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.StudentProgress;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StudentActivityProgressRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.repository.StudentProgressRepository;
import com.example.aichinese.repository.TracingExerciseRepository;

@Service
public class StudentLearningProgressService {

    private static final int FLASHCARD_COMPLETION_POINTS = 5;
    private static final int MATCHING_GAME_POINTS = 10;
    private static final int LISTENING_EXERCISE_POINTS = 10;
    private static final int TRACING_EXERCISE_POINTS = 10;
    private static final int QUIZ_COMPLETION_POINTS = 20;
    private static final int QUIZ_BONUS_POINTS = 10;
    private static final int LESSON_COMPLETION_POINTS = 25;

    private static final String SOURCE_FLASHCARD_SET = "FLASHCARD_SET";
    private static final String SOURCE_MATCHING_GAME = "MATCHING_GAME";
    private static final String SOURCE_LISTENING_EXERCISE = "LISTENING_EXERCISE";
    private static final String SOURCE_TRACING_EXERCISE = "TRACING_EXERCISE";
    private static final String SOURCE_QUIZ_COMPLETION = "QUIZ_COMPLETION";
    private static final String SOURCE_QUIZ_BONUS = "QUIZ_BONUS";
    private static final String SOURCE_LESSON_COMPLETION = "LESSON_COMPLETION";

    private final StudentActivityProgressRepository studentActivityProgressRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final DailyStreakRepository dailyStreakRepository;
    private final BadgeRepository badgeRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuizRepository quizRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;

    public StudentLearningProgressService(
            StudentActivityProgressRepository studentActivityProgressRepository,
            LessonProgressRepository lessonProgressRepository,
            StudentProgressRepository studentProgressRepository,
            PointsTransactionRepository pointsTransactionRepository,
            DailyStreakRepository dailyStreakRepository,
            BadgeRepository badgeRepository,
            StudentBadgeRepository studentBadgeRepository,
            FlashcardRepository flashcardRepository,
            QuizRepository quizRepository,
            MatchingGameRepository matchingGameRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository) {
        this.studentActivityProgressRepository = studentActivityProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.dailyStreakRepository = dailyStreakRepository;
        this.badgeRepository = badgeRepository;
        this.studentBadgeRepository = studentBadgeRepository;
        this.flashcardRepository = flashcardRepository;
        this.quizRepository = quizRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.listeningExerciseRepository = listeningExerciseRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizAttemptAnswerRepository = quizAttemptAnswerRepository;
    }

    @Transactional
    public CompletionOutcome completeFlashcard(StudentProfile student, Flashcard flashcard) {
        boolean firstCardCompletion = markCompleted(
                student,
                flashcard.getLesson(),
                "FLASHCARD",
                flashcard.getId(),
                "Practised flashcard: " + flashcard.getFrontText());
        touchDailyStreak(student);

        int earnedPoints = 0;
        if (isFlashcardSetCompleted(student, flashcard.getLesson())) {
            earnedPoints += awardPointsIfFirst(
                    student,
                    SOURCE_FLASHCARD_SET,
                    flashcard.getLesson().getId(),
                    FLASHCARD_COMPLETION_POINTS,
                    "Completed flashcard practice");
        }

        int lessonCompletionPercent = updateLessonAndStudentProgress(
                student,
                flashcard.getLesson(),
                "Practised flashcards");
        return new CompletionOutcome(firstCardCompletion, earnedPoints, totalPoints(student), lessonCompletionPercent);
    }

    @Transactional
    public CompletionOutcome completeMatchingGame(StudentProfile student, MatchingGame game, boolean completed) {
        boolean firstCompletion = recordActivity(
                student,
                game.getLesson(),
                "MATCHING",
                game.getId(),
                completed,
                "Completed matching game: " + game.getTitle());
        touchDailyStreak(student);

        int earnedPoints = completed
                ? awardPointsIfFirst(student, SOURCE_MATCHING_GAME, game.getId(), MATCHING_GAME_POINTS,
                        "Completed matching game")
                : 0;
        int lessonCompletionPercent = updateLessonAndStudentProgress(
                student,
                game.getLesson(),
                completed ? "Completed matching game" : "Tried matching game");
        return new CompletionOutcome(firstCompletion, earnedPoints, totalPoints(student), lessonCompletionPercent);
    }

    @Transactional
    public CompletionOutcome completeListeningExercise(StudentProfile student, ListeningExercise exercise) {
        boolean firstCompletion = markCompleted(
                student,
                exercise.getLesson(),
                "LISTENING",
                exercise.getId(),
                "Completed listening exercise: " + exercise.getTitle());
        touchDailyStreak(student);

        int earnedPoints = awardPointsIfFirst(
                student,
                SOURCE_LISTENING_EXERCISE,
                exercise.getId(),
                LISTENING_EXERCISE_POINTS,
                "Completed listening exercise");
        int lessonCompletionPercent = updateLessonAndStudentProgress(
                student,
                exercise.getLesson(),
                "Completed listening exercise");
        return new CompletionOutcome(firstCompletion, earnedPoints, totalPoints(student), lessonCompletionPercent);
    }

    @Transactional
    public CompletionOutcome completeTracingExercise(StudentProfile student, TracingExercise exercise) {
        boolean firstCompletion = markCompleted(
                student,
                exercise.getLesson(),
                "TRACING",
                exercise.getId(),
                "Completed tracing exercise: " + exercise.getTitle());
        touchDailyStreak(student);

        int earnedPoints = awardPointsIfFirst(
                student,
                SOURCE_TRACING_EXERCISE,
                exercise.getId(),
                TRACING_EXERCISE_POINTS,
                "Completed tracing exercise");
        int lessonCompletionPercent = updateLessonAndStudentProgress(
                student,
                exercise.getLesson(),
                "Completed tracing exercise");
        return new CompletionOutcome(firstCompletion, earnedPoints, totalPoints(student), lessonCompletionPercent);
    }

    @Transactional
    public CompletionOutcome completeQuiz(StudentProfile student, Quiz quiz, int percentageScore) {
        boolean firstCompletion = markCompleted(
                student,
                quiz.getLesson(),
                "QUIZ",
                quiz.getId(),
                "Submitted quiz: " + quiz.getTitle());
        touchDailyStreak(student);

        int earnedPoints = awardPointsIfFirst(
                student,
                SOURCE_QUIZ_COMPLETION,
                quiz.getId(),
                QUIZ_COMPLETION_POINTS,
                "Completed quiz");

        if (percentageScore >= 80) {
            earnedPoints += awardPointsIfFirst(
                    student,
                    SOURCE_QUIZ_BONUS,
                    quiz.getId(),
                    QUIZ_BONUS_POINTS,
                    "Achieved a high quiz score");
            awardBadgeIfNeeded(student, "Quiz Star", "Scored at least 80% in a quiz.", "quiz-star");
        }

        int lessonCompletionPercent = updateLessonAndStudentProgress(
                student,
                quiz.getLesson(),
                "Submitted quiz");
        return new CompletionOutcome(firstCompletion, earnedPoints, totalPoints(student), lessonCompletionPercent);
    }

    @Transactional(readOnly = true)
    public boolean isCompleted(StudentProfile student, String activityType, Long sourceId) {
        return studentActivityProgressRepository
                .findByStudentIdAndActivityTypeAndActivitySourceId(student.getId(), activityType, sourceId)
                .map(StudentActivityProgress::isCompleted)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public int lessonCompletionPercent(StudentProfile student, Lesson lesson) {
        return lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId())
                .map(progress -> progress.getCompletionPercent() == null ? 0 : progress.getCompletionPercent())
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public StudentProgressSummaryResponse getProgressSummary(StudentProfile student) {
        List<LessonProgress> lessonProgressItems = lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId());
        StudentProgress studentProgress = studentProgressRepository.findByStudentId(student.getId()).orElse(null);
        int startedLessons = (int) lessonProgressRepository.countByStudentId(student.getId());
        int completedLessons = (int) lessonProgressRepository.countByStudentIdAndCompletedTrue(student.getId());
        int quizAverage = round(
                quizAttemptRepository.findAveragePercentageByStudentId(student.getId()) == null
                        ? 0
                        : quizAttemptRepository.findAveragePercentageByStudentId(student.getId()));

        List<String> weakAreas = extractWeakAreas(studentProgress);
        List<StudentProgressLessonResponse> recentLessons = lessonProgressItems.stream()
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
                studentProgress != null && studentProgress.getCompletionPercent() != null
                        ? studentProgress.getCompletionPercent()
                        : 0,
                weakAreas,
                studentProgress != null ? studentProgress.getLastActivitySummary() : null,
                recentLessons);
    }

    @Transactional(readOnly = true)
    public RewardSummaryResponse getRewardSummary(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findFirstByStudentIdOrderByCreatedAtDesc(student.getId()).orElse(null);
        List<BadgeSummaryResponse> badges = studentBadgeRepository.findByStudentIdOrderByAwardedAtDesc(student.getId()).stream()
                .map(badge -> new BadgeSummaryResponse(
                        badge.getBadge().getId(),
                        badge.getBadge().getName(),
                        badge.getBadge().getDescription(),
                        badge.getBadge().getIconKey(),
                        badge.getAwardedAt()))
                .toList();

        List<PointsTransactionResponse> recentPoints = pointsTransactionRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())
                .stream()
                .limit(10)
                .map(transaction -> new PointsTransactionResponse(
                        transaction.getId(),
                        transaction.getPointsDelta(),
                        transaction.getReason(),
                        transaction.getSourceType(),
                        transaction.getSourceId(),
                        transaction.getCreatedAt()))
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
        return getRewardSummary(student).badges();
    }

    @Transactional(readOnly = true)
    public List<PointsTransactionResponse> getPointsHistory(StudentProfile student) {
        return pointsTransactionRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(transaction -> new PointsTransactionResponse(
                        transaction.getId(),
                        transaction.getPointsDelta(),
                        transaction.getReason(),
                        transaction.getSourceType(),
                        transaction.getSourceId(),
                        transaction.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DailyStreakResponse getStreak(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findFirstByStudentIdOrderByCreatedAtDesc(student.getId()).orElse(null);
        return new DailyStreakResponse(
                streak != null && streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0,
                streak != null && streak.getBestStreak() != null ? streak.getBestStreak() : 0,
                streak != null ? streak.getLastLearningDate() : null);
    }

    private boolean isFlashcardSetCompleted(StudentProfile student, Lesson lesson) {
        List<Flashcard> flashcards = flashcardRepository.findByLessonIdOrderBySortOrderAscCreatedAtAsc(lesson.getId());
        if (flashcards.isEmpty()) {
            return false;
        }

        return flashcards.stream().allMatch(flashcard -> isCompleted(student, "FLASHCARD", flashcard.getId()));
    }

    private boolean markCompleted(StudentProfile student, Lesson lesson, String activityType, Long sourceId, String summary) {
        return recordActivity(student, lesson, activityType, sourceId, true, summary);
    }

    private boolean recordActivity(
            StudentProfile student,
            Lesson lesson,
            String activityType,
            Long sourceId,
            boolean completed,
            String summary) {
        StudentActivityProgress progress = studentActivityProgressRepository
                .findByStudentIdAndActivityTypeAndActivitySourceId(student.getId(), activityType, sourceId)
                .orElseGet(StudentActivityProgress::new);
        boolean firstCompletion = !progress.isCompleted() && completed;
        LocalDateTime now = LocalDateTime.now();

        if (progress.getId() == null) {
            progress.setStudent(student);
            progress.setLesson(lesson);
            progress.setActivityType(activityType);
            progress.setActivitySourceId(sourceId);
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
        studentActivityProgressRepository.save(progress);
        return firstCompletion;
    }

    private void touchDailyStreak(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findFirstByStudentIdOrderByCreatedAtDesc(student.getId())
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

    private int updateLessonAndStudentProgress(StudentProfile student, Lesson lesson, String lastActivitySummary) {
        int totalActivities = countTotalActivities(lesson);
        long completedActivities = studentActivityProgressRepository.findByStudentIdAndLessonIdOrderByUpdatedAtDesc(
                student.getId(),
                lesson.getId()).stream()
                .filter(StudentActivityProgress::isCompleted)
                .count();

        int percent = totalActivities == 0 ? 0 : (int) Math.round((completedActivities * 100.0) / totalActivities);
        boolean completed = totalActivities > 0 && completedActivities >= totalActivities;

        LessonProgress lessonProgress = lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId())
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

        updateStudentProgressSummary(student, lastActivitySummary);
        return percent;
    }

    private void updateStudentProgressSummary(StudentProfile student, String lastActivitySummary) {
        StudentProgress studentProgress = studentProgressRepository.findByStudentId(student.getId())
                .orElseGet(StudentProgress::new);

        if (studentProgress.getId() == null) {
            studentProgress.setStudent(student);
            studentProgress.setLevel(student.getCurrentLevel());
        }

        long startedLessons = lessonProgressRepository.countByStudentId(student.getId());
        long completedLessons = lessonProgressRepository.countByStudentIdAndCompletedTrue(student.getId());
        int overallCompletion = startedLessons == 0
                ? 0
                : (int) Math.round((completedLessons * 100.0) / startedLessons);

        List<String> weakAreas = quizAttemptAnswerRepository
                .findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(student.getId()).stream()
                .map(answer -> answer.getQuestion().getQuestionType())
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .toList();

        studentProgress.setCompletionPercent(overallCompletion);
        studentProgress.setWeakAreas(weakAreas.isEmpty() ? null : String.join(", ", weakAreas));
        studentProgress.setLastActivitySummary(lastActivitySummary);
        studentProgressRepository.save(studentProgress);
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
        if (student.getTotalPoints() >= 50) {
            awardBadgeIfNeeded(student, "Point Starter", "Collected at least 50 learning points.", "point-starter");
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

    private int countTotalActivities(Lesson lesson) {
        return (int) (flashcardRepository.countByLessonId(lesson.getId())
                + quizRepository.countByLessonId(lesson.getId())
                + matchingGameRepository.countByLessonId(lesson.getId())
                + listeningExerciseRepository.countByLessonId(lesson.getId())
                + tracingExerciseRepository.countByLessonId(lesson.getId()));
    }

    private int totalPoints(StudentProfile student) {
        return student.getTotalPoints() == null ? 0 : student.getTotalPoints();
    }

    private int round(double value) {
        return (int) Math.round(value);
    }

    private List<String> extractWeakAreas(StudentProgress studentProgress) {
        if (studentProgress == null || studentProgress.getWeakAreas() == null || studentProgress.getWeakAreas().isBlank()) {
            return List.of();
        }

        return List.of(studentProgress.getWeakAreas().split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public record CompletionOutcome(
            boolean firstCompletion,
            int earnedPoints,
            int totalPoints,
            int lessonCompletionPercent) {
    }
}
