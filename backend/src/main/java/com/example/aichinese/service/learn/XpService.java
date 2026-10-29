package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.example.aichinese.dto.learn.DailyGoalResponse;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.dto.learn.XpResult;
import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;

/**
 * XP, the daily goal, the streak and badges. Every award goes through an {@link XpSession}, which holds the student's
 * row lock, writes points transactions (UNIQUE(student, source) makes a repeated award a no-op), and on
 * {@link XpSession#finish()} pays the daily-goal bonus when today's XP first reaches the goal, extends the streak
 * (a streak day is a day the goal was reached) and awards the badges the new totals earn.
 */
@Service
public class XpService {

    public static final String SOURCE_LEARN_LESSON = "LEARN_LESSON";
    public static final String SOURCE_PRACTICE_PREFIX = "PRACTICE_";
    public static final String SOURCE_QUIZ_STREAK = "QUIZ_STREAK";
    public static final String SOURCE_MISTAKES_REVIEW = "MISTAKES_REVIEW";
    public static final String SOURCE_DAILY_GOAL = "DAILY_GOAL";

    private static final int WEEK_DAYS = 7;

    private final StudentProfileRepository studentProfileRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final DailyStreakRepository dailyStreakRepository;
    private final BadgeRepository badgeRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final LearnLessonCompletionRepository lessonCompletionRepository;
    private final PracticeCompletionRepository practiceCompletionRepository;
    private final LearningMistakeRepository mistakeRepository;
    private final LearnClock clock;
    private final EntityManager entityManager;

    public XpService(
            StudentProfileRepository studentProfileRepository,
            PointsTransactionRepository pointsTransactionRepository,
            DailyStreakRepository dailyStreakRepository,
            BadgeRepository badgeRepository,
            StudentBadgeRepository studentBadgeRepository,
            LearnLessonCompletionRepository lessonCompletionRepository,
            PracticeCompletionRepository practiceCompletionRepository,
            LearningMistakeRepository mistakeRepository,
            LearnClock clock,
            EntityManager entityManager) {
        this.studentProfileRepository = studentProfileRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.dailyStreakRepository = dailyStreakRepository;
        this.badgeRepository = badgeRepository;
        this.studentBadgeRepository = studentBadgeRepository;
        this.lessonCompletionRepository = lessonCompletionRepository;
        this.practiceCompletionRepository = practiceCompletionRepository;
        this.mistakeRepository = mistakeRepository;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    public static String practiceSource(PracticeType type) {
        return SOURCE_PRACTICE_PREFIX + type.name();
    }

    /** Starts awarding for one request; call inside the caller's transaction. */
    public XpSession begin(StudentProfile student) {
        return new XpSession(lock(student));
    }

    /** Awards one badge outside an XP session (a class quiz score); no-op when the student already has it. */
    @Transactional
    public boolean awardBadge(StudentProfile student, RewardBadge rewardBadge) {
        return grantBadge(student, rewardBadge, clock.now());
    }

    // ===== Reads =====

    @Transactional(readOnly = true)
    public int todayXp(StudentProfile student) {
        LocalDate today = clock.today();
        return (int) pointsTransactionRepository.sumBetween(
                student.getId(), clock.startOf(today), clock.startOf(today.plusDays(1)));
    }

    @Transactional(readOnly = true)
    public boolean goalReachedOn(StudentProfile student, LocalDate day) {
        return pointsTransactionRepository.existsByStudentIdAndSourceTypeAndSourceId(
                student.getId(), SOURCE_DAILY_GOAL, day.toEpochDay());
    }

    /** Days (in {@code [from, to]}) on which the student reached the daily goal. */
    @Transactional(readOnly = true)
    public Set<LocalDate> goalDays(StudentProfile student, LocalDate from, LocalDate to) {
        return pointsTransactionRepository.findSourceIdsBetween(
                        student.getId(), SOURCE_DAILY_GOAL, from.toEpochDay(), to.toEpochDay()).stream()
                .map(LocalDate::ofEpochDay)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public StreakState streak(StudentProfile student) {
        DailyStreak streak = dailyStreakRepository.findByStudentId(student.getId()).orElse(null);
        if (streak == null) {
            return new StreakState(0, 0);
        }
        int current = RewardRules.currentStreak(streak.getLastLearningDate(), count(streak.getCurrentStreak()), clock.today());
        return new StreakState(current, Math.max(count(streak.getBestStreak()), current));
    }

    @Transactional(readOnly = true)
    public TodayResponse today(StudentProfile student) {
        LocalDate today = clock.today();
        LocalDate weekStart = today.minusDays(WEEK_DAYS - 1L);
        Set<LocalDate> reached = goalDays(student, weekStart, today);
        List<TodayResponse.Day> week = new ArrayList<>(WEEK_DAYS);
        for (LocalDate day = weekStart; !day.isAfter(today); day = day.plusDays(1)) {
            week.add(new TodayResponse.Day(day, reached.contains(day)));
        }
        long setsToday = practiceCompletionRepository.countByStudentIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                student.getId(), clock.startOf(today), clock.startOf(today.plusDays(1)));
        return new TodayResponse(
                today,
                dailyGoal(student),
                todayXp(student),
                reached.contains(today),
                streak(student).current(),
                week,
                setsToday,
                mistakeRepository.countByStudentIdAndClearedAtIsNull(student.getId()));
    }

    @Transactional(readOnly = true)
    public DailyGoalResponse dailyGoalOf(StudentProfile student) {
        return new DailyGoalResponse(dailyGoal(student));
    }

    /**
     * Changes the daily goal. A lower goal that today's XP already meets counts as reached today (bonus and streak),
     * the same as reaching it with the next lesson.
     */
    @Transactional
    public DailyGoalResponse changeDailyGoal(StudentProfile student, Integer dailyGoalXp) {
        if (!RewardRules.isDailyGoalOption(dailyGoalXp)) {
            throw new ResponseStatusException(BAD_REQUEST, "Daily goal must be 10, 20 or 30 XP.");
        }
        XpSession session = begin(student);
        session.student().setDailyGoalXp(dailyGoalXp);
        session.finish();
        student.setDailyGoalXp(dailyGoalXp);
        return dailyGoalOf(session.student());
    }

    // ===== Internals =====

    private StudentProfile lock(StudentProfile student) {
        StudentProfile locked = studentProfileRepository.findLockedById(student.getId()).orElse(student);
        // The query hands back the instance already in the persistence context without reloading it; a locking
        // refresh sees the latest committed total even under MySQL's REPEATABLE READ, so concurrent awards add up.
        if (entityManager.contains(locked)) {
            entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        }
        return locked;
    }

    private static int dailyGoal(StudentProfile student) {
        Integer goal = student.getDailyGoalXp();
        return RewardRules.isDailyGoalOption(goal) ? goal : RewardRules.DEFAULT_DAILY_GOAL_XP;
    }

    private boolean grantBadge(StudentProfile student, RewardBadge rewardBadge, LocalDateTime at) {
        // DataSeeder seeds the catalogue; creating the row here only keeps an award from failing on a database that
        // was seeded differently.
        Badge badge = badgeRepository.findByName(rewardBadge.badgeName()).orElseGet(() -> {
            Badge created = new Badge();
            created.setName(rewardBadge.badgeName());
            created.setDescription(rewardBadge.description());
            created.setIconKey(rewardBadge.iconKey());
            created.setActive(true);
            return badgeRepository.save(created);
        });
        if (studentBadgeRepository.existsByStudentIdAndBadgeId(student.getId(), badge.getId())) {
            return false;
        }
        StudentBadge studentBadge = new StudentBadge();
        studentBadge.setStudent(student);
        studentBadge.setBadge(badge);
        studentBadge.setAwardedAt(at);
        studentBadgeRepository.save(studentBadge);
        return true;
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    /** The streak as it stands today and the best streak. */
    public record StreakState(int current, int best) {
    }

    /** One request's awards for one locked student. */
    public final class XpSession {

        private final StudentProfile student;
        private final List<String> newBadges = new ArrayList<>();
        private int xpEarned;
        private boolean finishedSomething;

        private XpSession(StudentProfile student) {
            this.student = student;
        }

        public StudentProfile student() {
            return student;
        }

        /** Awards XP once per source; returns the XP written now (0 when this source was already paid). */
        public int award(String sourceType, Long sourceId, int xp, String title) {
            if (xp <= 0 || pointsTransactionRepository.existsByStudentIdAndSourceTypeAndSourceId(
                    student.getId(), sourceType, sourceId)) {
                return 0;
            }
            write(sourceType, sourceId, xp, title);
            xpEarned += xp;
            return xp;
        }

        /** A lesson or set was finished: First Steps and Lesson Finisher are checked on finish. */
        public void finishedLessonOrSet() {
            finishedSomething = true;
        }

        public void badge(RewardBadge rewardBadge) {
            if (grantBadge(student, rewardBadge, clock.now())) {
                newBadges.add(rewardBadge.name());
            }
        }

        /** Pays the daily-goal bonus when reached, extends the streak and awards the badges earned. */
        public XpResult finish() {
            LocalDate today = clock.today();
            int goal = dailyGoal(student);
            int todayXp = todayXp(student);
            boolean goalReached = goalReachedOn(student, today);
            boolean goalJustReached = false;
            int bonus = 0;
            if (!goalReached && todayXp >= goal) {
                bonus = RewardRules.DAILY_GOAL_BONUS_XP;
                write(SOURCE_DAILY_GOAL, today.toEpochDay(), bonus, "Daily goal reached");
                todayXp += bonus;
                goalReached = true;
                goalJustReached = true;
                extendStreak(today);
            }
            int totalXp = count(student.getTotalPoints());
            if (totalXp >= RewardRules.XP_STARTER_THRESHOLD) {
                badge(RewardBadge.XP_STARTER);
            }
            if (totalXp >= RewardRules.XP_300_THRESHOLD) {
                badge(RewardBadge.XP_300);
            }
            if (finishedSomething) {
                long finished = lessonCompletionRepository.countByStudentId(student.getId())
                        + practiceCompletionRepository.findFinishedSets(student.getId()).size();
                if (finished >= 1) {
                    badge(RewardBadge.FIRST_STEPS);
                }
                if (finished >= RewardRules.LESSON_FINISHER_COUNT) {
                    badge(RewardBadge.LESSON_FINISHER);
                }
            }
            int streak = streak(student).current();
            return new XpResult(xpEarned, bonus, totalXp, todayXp, goal, goalReached, goalJustReached, streak, newBadges);
        }

        private void write(String sourceType, Long sourceId, int xp, String title) {
            PointsTransaction transaction = new PointsTransaction();
            transaction.setStudent(student);
            transaction.setPointsDelta(xp);
            transaction.setReason(title == null || title.isBlank() ? sourceType : title);
            transaction.setSourceType(sourceType);
            transaction.setSourceId(sourceId);
            pointsTransactionRepository.saveAndFlush(transaction);
            student.setTotalPoints(count(student.getTotalPoints()) + xp);
        }

        private void extendStreak(LocalDate today) {
            DailyStreak streak = dailyStreakRepository.findLockedByStudentId(student.getId()).orElseGet(() -> {
                DailyStreak created = new DailyStreak();
                created.setStudent(student);
                return created;
            });
            int next = RewardRules.nextStreak(streak.getLastLearningDate(), count(streak.getCurrentStreak()), today);
            streak.setCurrentStreak(next);
            streak.setBestStreak(Math.max(count(streak.getBestStreak()), next));
            streak.setLastLearningDate(today);
            dailyStreakRepository.save(streak);
            if (next >= RewardRules.STREAK_EXPLORER_DAYS) {
                badge(RewardBadge.STREAK_EXPLORER);
            }
            if (next >= RewardRules.TEN_DAY_STREAK_DAYS) {
                badge(RewardBadge.TEN_DAY_STREAK);
            }
        }
    }
}
