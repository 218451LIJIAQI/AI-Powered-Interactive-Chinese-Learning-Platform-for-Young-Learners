package com.example.aichinese.service.learn;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.service.ScheduledJobRunner;
import com.example.aichinese.util.ExceptionLogUtils;

import lombok.RequiredArgsConstructor;

/**
 * The learners' scheduled reminders (APP_SCHEDULING_ENABLED), on the platform's clock (APP_DEFAULT_TIME_ZONE):
 * <ul>
 *   <li>the daily-goal reminder, once a day from 19:00: learners with a plan who earned XP in the last 7 days and have
 *       not reached today's goal hear how much XP is still missing;</li>
 *   <li>the mistakes reminder, once a week from Monday 10:00: learners with 5 or more mistakes waiting.</li>
 * </ul>
 * Each job keeps the day of its last run in system_settings and moves it on with a conditional update, as the admins'
 * daily summary does, so a restart or a second server never sends a reminder twice.
 */
@Service
@RequiredArgsConstructor
public class LearnerReminderJobs {

    private static final Logger log = LoggerFactory.getLogger(LearnerReminderJobs.class);

    public static final String DAILY_GOAL_LAST_RUN_KEY = "daily_goal_reminder_last_day";
    public static final String MISTAKES_LAST_RUN_KEY = "mistakes_reminder_last_week";
    private static final LocalTime DAILY_GOAL_FROM = LocalTime.of(19, 0);
    private static final LocalTime MISTAKES_FROM = LocalTime.of(10, 0);
    /** Learners who earned XP in this many days get the daily-goal reminder. */
    private static final int ACTIVE_DAYS = 7;
    /** The mistakes reminder waits for this many. */
    private static final long MIN_MISTAKES = 5;
    private static final int REMINDER_BATCH_SIZE = 500;

    private final StudentProfileRepository studentProfileRepository;
    private final LearningMistakeRepository mistakeRepository;
    private final ScheduledJobRunner jobs;
    private final LearnerNotifications learnerNotifications;
    private final LearnClock learnClock;

    /** Every minute while background jobs run (app.scheduling.enabled). */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void runIfDue() {
        Clock clock = Clock.systemDefaultZone();
        runIsolated("daily-goal reminder", () -> remindDailyGoalIfDue(clock));
        runIsolated("mistakes reminder", () -> remindMistakesIfDue(clock));
    }

    private static void runIsolated(String jobName, Runnable task) {
        try {
            task.run();
        } catch (Exception exception) {
            log.warn("Scheduled {} job failed: {}", jobName, ExceptionLogUtils.safeSummary(exception));
            ExceptionLogUtils.debugOrigin(log, exception);
        }
    }

    /** The day's daily-goal reminders, if due; returns how many learners were reminded. */
    public int remindDailyGoalIfDue(Clock clock) {
        LocalDateTime now = learnClock.dateTimeAt(clock.instant());
        if (now.toLocalTime().isBefore(DAILY_GOAL_FROM)) {
            return 0;
        }
        LocalDate today = now.toLocalDate();
        if (!jobs.claimPeriod(DAILY_GOAL_LAST_RUN_KEY, today, clock)) {
            return 0;
        }
        LocalDateTime activeSince = learnClock.startOf(today.minusDays(ACTIVE_DAYS - 1L));
        LocalDateTime dayStart = learnClock.startOf(today);
        LocalDateTime dayEnd = learnClock.startOf(today.plusDays(1));
        long epochDay = today.toEpochDay();
        return dispatchBatched(
                (afterUserId, batchPage) -> studentProfileRepository.findDailyGoalsForReminder(
                        activeSince, dayStart, dayEnd, XpService.SOURCE_DAILY_GOAL, epochDay, afterUserId, batchPage),
                StudentProfileRepository.DailyGoal::learnerId,
                StudentProfileRepository.DailyGoal::remainingXp,
                learnerNotifications::dailyGoalReminders);
    }

    /** The week's mistakes reminders, if due (from Monday 10:00); returns how many learners were reminded. */
    public int remindMistakesIfDue(Clock clock) {
        LocalDateTime now = learnClock.dateTimeAt(clock.instant());
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (now.toLocalDate().equals(monday) && now.toLocalTime().isBefore(MISTAKES_FROM)) {
            return 0;
        }
        if (!jobs.claimPeriod(MISTAKES_LAST_RUN_KEY, monday, clock)) {
            return 0;
        }
        return dispatchBatched(
                (afterUserId, batchPage) -> mistakeRepository.findLearnersWithWaitingMistakes(
                        MIN_MISTAKES, afterUserId, batchPage),
                LearningMistakeRepository.Waiting::userId,
                LearningMistakeRepository.Waiting::waiting,
                learnerNotifications::mistakesToReviewReminders);
    }

    private static <T> int dispatchBatched(
            BiFunction<Long, PageRequest, List<T>> fetchSlice,
            ToLongFunction<T> userIdOf,
            ToLongFunction<T> metricOf,
            Consumer<Map<Long, Long>> sendBatch) {
        long afterUserId = 0L;
        int totalReminded = 0;
        PageRequest batchPage = PageRequest.of(0, REMINDER_BATCH_SIZE);
        while (true) {
            List<T> slice = fetchSlice.apply(afterUserId, batchPage);
            if (slice.isEmpty()) {
                break;
            }
            Map<Long, Long> reminders = new LinkedHashMap<>(slice.size());
            for (T item : slice) {
                long userId = userIdOf.applyAsLong(item);
                reminders.put(userId, metricOf.applyAsLong(item));
                afterUserId = userId;
            }
            sendBatch.accept(reminders);
            totalReminded += reminders.size();
            if (slice.size() < REMINDER_BATCH_SIZE) {
                break;
            }
        }
        return totalReminded;
    }
}
