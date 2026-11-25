package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.DailyGoalResponse;
import com.example.aichinese.dto.learn.RewardSummaryResponse;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.dto.learn.XpResult;
import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.StudentLearningAccessService;

import lombok.RequiredArgsConstructor;

/**
 * XP, the daily goal, the streak and badges. Every XP award goes through an {@link XpSession}, which holds the
 * student's row lock, writes points transactions once per source (a repeated award writes nothing, and
 * UNIQUE(student, source) backs that up), and on {@link XpSession#finish()} pays the daily-goal bonus when today's XP
 * first reaches the goal, extends the streak (a streak day is a day the goal was reached) and awards the badges the new
 * totals earn.
 */
@Service
@RequiredArgsConstructor
public class XpService {

    public static final String SOURCE_LEARN_LESSON = "LEARN_LESSON";
    public static final String SOURCE_MISTAKES_REVIEW = "MISTAKES_REVIEW";
    public static final String SOURCE_DAILY_GOAL = "DAILY_GOAL";

    private static final int WEEK_DAYS = 7;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final DailyStreakRepository dailyStreakRepository;
    private final BadgeRepository badgeRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final LearnLessonCompletionRepository lessonCompletionRepository;
    private final PracticeCompletionRepository practiceCompletionRepository;
    private final LearningMistakeRepository mistakeRepository;
    private final LearnerNotifications learnerNotifications;
    private final LearnClock clock;
    private final StudentLearningAccessService accessService;

    /**
     * Starts awarding inside the caller's transaction, which must be read committed: the XP and badge lookups after the
     * student lock then see the awards committed while the caller waited for it, and the locked lookup of a learner's
     * missing streak row locks no index gap, which another learner's first streak could share and deadlock on.
     */
    public XpSession begin(StudentProfile student) {
        return new XpSession(accessService.lockProfile(student));
    }

    /** Locks the learner so concurrent quiz submissions can award the same badge only once. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void awardBadge(StudentProfile student, RewardBadge rewardBadge) {
        StudentProfile locked = accessService.lockProfile(student);
        String badgeName = rewardBadge.badgeName();
        if (!studentBadgeRepository.existsByStudentIdAndBadgeName(locked.getId(), badgeName)) {
            saveStudentBadge(locked, badgeName, clock.serverTimeAt(clock.now()));
        }
    }

    // Reads

    /** What the student sidebar shows: total XP and the current streak. */
    @Transactional(readOnly = true)
    public RewardSummaryResponse rewardSummary(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        return new RewardSummaryResponse(student.getTotalPoints(), streak(student).current());
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
        return streak(student, clock.today());
    }

    StreakState streak(StudentProfile student, LocalDate today) {
        return dailyStreakRepository.findByStudentId(student.getId())
                .map(streak -> new StreakState(
                        RewardRules.currentStreak(streak.getLastLearningDate(), streak.getCurrentStreak(), today),
                        streak.getBestStreak()))
                .orElse(new StreakState(0, 0));
    }

    @Transactional(readOnly = true)
    public TodayResponse today(SecurityUserDetails currentUser) {
        return today(accessService.requireStudent(currentUser));
    }

    @Transactional(readOnly = true)
    public TodayResponse today(StudentProfile student) {
        LocalDate today = clock.today();
        LocalDate weekStart = today.minusDays(WEEK_DAYS - 1L);
        Set<LocalDate> reached = goalDays(student, weekStart, today);
        return new TodayResponse(
                student.getDailyGoalXp(),
                todayXp(student, today),
                reached.contains(today),
                weekStart.datesUntil(today.plusDays(1)).map(day -> new TodayResponse.Day(day, reached.contains(day))).toList(),
                mistakeRepository.countByStudentIdAndClearedAtIsNull(student.getId()));
    }

    @Transactional(readOnly = true)
    public DailyGoalResponse dailyGoalOf(SecurityUserDetails currentUser) {
        return dailyGoalOf(accessService.requireStudent(currentUser));
    }

    private static DailyGoalResponse dailyGoalOf(StudentProfile student) {
        return new DailyGoalResponse(student.getDailyGoalXp());
    }

    /**
     * Changes the daily goal. A lower goal that today's XP already meets counts as reached today (bonus and streak),
     * the same as reaching it with the next lesson.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DailyGoalResponse changeDailyGoal(SecurityUserDetails currentUser, Integer dailyGoalXp) {
        if (!RewardRules.isDailyGoalOption(dailyGoalXp)) {
            throw new ResponseStatusException(BAD_REQUEST, "Daily goal must be 10, 20 or 30 XP.");
        }
        XpSession session = begin(accessService.requireStudent(currentUser));
        session.student().setDailyGoalXp(dailyGoalXp);
        session.finish();
        return dailyGoalOf(session.student());
    }

    private int todayXp(StudentProfile student, LocalDate today) {
        return (int) pointsTransactionRepository.sumBetween(
                student.getId(), clock.startOf(today), clock.startOf(today.plusDays(1)));
    }

    private void saveStudentBadge(StudentProfile student, String badgeName, LocalDateTime at) {
        Badge badge = badgeRepository.findByName(badgeName).orElseThrow();
        StudentBadge studentBadge = new StudentBadge();
        studentBadge.setStudent(student);
        studentBadge.setBadge(badge);
        studentBadge.setAwardedAt(at);
        studentBadge.setCreatedAt(at);
        studentBadge.setUpdatedAt(at);
        studentBadgeRepository.save(studentBadge);
    }

    /** The streak as it stands today and the best streak. */
    public record StreakState(int current, int best) {
    }

    /** One request's awards for one locked student. */
    public final class XpSession {

        private final StudentProfile student;
        /** The total XP before this request's awards: a rank passed since then is a rank-up. */
        private final int startXp;
        /** Single instant captured at session start so transaction timestamps and platform day never skew across midnight. */
        private final LocalDate sessionDay;
        private final LocalDateTime sessionTimestamp;
        private final List<String> newBadges = new ArrayList<>();
        /** Loaded lazily on the first badge check so a session runs at most one earned-badges lookup. */
        private Set<String> earnedBadgeNames;
        private int xpEarned;
        private boolean finishedSomething;

        private XpSession(StudentProfile student) {
            this.student = student;
            this.startXp = student.getTotalPoints();
            Instant now = clock.now();
            this.sessionDay = clock.dayAt(now);
            this.sessionTimestamp = clock.serverTimeAt(now);
        }

        public StudentProfile student() {
            return student;
        }

        LocalDate sessionDay() {
            return sessionDay;
        }

        LocalDateTime sessionTimestamp() {
            return sessionTimestamp;
        }

        /** Awards XP once per source: a source that was already paid writes nothing. */
        public void award(String sourceType, Long sourceId, int xp, String title) {
            if (xp <= 0 || pointsTransactionRepository.existsByStudentIdAndSourceTypeAndSourceId(
                    student.getId(), sourceType, sourceId)) {
                return;
            }
            write(sourceType, sourceId, xp, title);
            xpEarned += xp;
        }

        /** A lesson or set was finished: First Steps and Lesson Finisher are checked on finish. */
        public void finishedLessonOrSet() {
            finishedSomething = true;
        }

        public void badge(RewardBadge rewardBadge) {
            String badgeName = rewardBadge.badgeName();
            if (earnedBadges().add(badgeName)) {
                saveStudentBadge(student, badgeName, sessionTimestamp);
                newBadges.add(rewardBadge.name());
            }
        }

        private Set<String> earnedBadges() {
            if (earnedBadgeNames == null) {
                earnedBadgeNames = new HashSet<>(studentBadgeRepository.findBadgeNamesByStudentId(student.getId()));
            }
            return earnedBadgeNames;
        }

        /**
         * Pays the daily-goal bonus when reached, extends the streak, awards the badges earned and, when the XP took the
         * learner past a rank threshold, says so (with a "You reached Rank N" notification).
         */
        public XpResult finish() {
            LocalDate today = sessionDay;
            int goal = student.getDailyGoalXp();
            int todayXp = todayXp(student, today);
            boolean reachedBefore = pointsTransactionRepository.existsByStudentIdAndSourceTypeAndSourceId(
                    student.getId(), SOURCE_DAILY_GOAL, today.toEpochDay());
            boolean goalJustReached = !reachedBefore && todayXp >= goal;
            int bonus = goalJustReached ? RewardRules.DAILY_GOAL_BONUS_XP : 0;
            int streak;
            if (goalJustReached) {
                write(SOURCE_DAILY_GOAL, today.toEpochDay(), bonus, "Daily goal reached");
                streak = extendStreak(today);
            } else {
                streak = streak(student, today).current();
            }
            int totalXp = student.getTotalPoints();
            if (totalXp >= RewardRules.XP_STARTER_THRESHOLD) {
                badge(RewardBadge.XP_STARTER);
            }
            if (totalXp >= RewardRules.XP_300_THRESHOLD) {
                badge(RewardBadge.XP_300);
            }
            if (finishedSomething && (!earnedBadges().contains(RewardBadge.FIRST_STEPS.badgeName())
                    || !earnedBadges().contains(RewardBadge.LESSON_FINISHER.badgeName()))) {
                long finished = lessonCompletionRepository.countByStudentIdAndLessonKinds(
                        student.getId(), LearnLessonKind.COUNTED_AS_LESSONS)
                        + practiceCompletionRepository.countFinishedSets(student.getId());
                if (finished >= 1) {
                    badge(RewardBadge.FIRST_STEPS);
                }
                if (finished >= RewardRules.LESSON_FINISHER_COUNT) {
                    badge(RewardBadge.LESSON_FINISHER);
                }
            }
            return new XpResult(xpEarned, bonus, todayXp + bonus, goal, reachedBefore || goalJustReached, streak,
                    newBadges, rankUp(totalXp));
        }

        private XpResult.RankUp rankUp(int totalXp) {
            int rank = RewardRules.levelFor(totalXp);
            if (rank <= RewardRules.levelFor(startXp)) {
                return null;
            }
            learnerNotifications.rankReached(student.getUser(), rank);
            return new XpResult.RankUp(rank, RewardRules.rankXp(rank), RewardRules.rankXp(rank + 1));
        }

        private void write(String sourceType, Long sourceId, int xp, String title) {
            PointsTransaction transaction = new PointsTransaction();
            transaction.setStudent(student);
            transaction.setPointsDelta(xp);
            transaction.setReason(title);
            transaction.setSourceType(sourceType);
            transaction.setSourceId(sourceId);
            transaction.setCreatedAt(sessionTimestamp);
            transaction.setUpdatedAt(sessionTimestamp);
            pointsTransactionRepository.saveAndFlush(transaction);
            student.setTotalPoints(student.getTotalPoints() + xp);
        }

        private int extendStreak(LocalDate today) {
            DailyStreak streak = dailyStreakRepository.findLockedByStudentId(student.getId()).orElseGet(() -> {
                DailyStreak created = new DailyStreak();
                created.setStudent(student);
                return created;
            });
            int next = RewardRules.nextStreak(streak.getLastLearningDate(), streak.getCurrentStreak(), today);
            streak.setCurrentStreak(next);
            streak.setBestStreak(Math.max(streak.getBestStreak(), next));
            streak.setLastLearningDate(today);
            dailyStreakRepository.save(streak);
            if (next >= RewardRules.STREAK_EXPLORER_DAYS) {
                badge(RewardBadge.STREAK_EXPLORER);
            }
            if (next >= RewardRules.TEN_DAY_STREAK_DAYS) {
                badge(RewardBadge.TEN_DAY_STREAK);
            }
            return next;
        }
    }
}
