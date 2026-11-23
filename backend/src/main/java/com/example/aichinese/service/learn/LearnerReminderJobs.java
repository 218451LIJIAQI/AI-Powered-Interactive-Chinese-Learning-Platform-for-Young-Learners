package com.example.aichinese.service.learn;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        return jobs.runOnce(DAILY_GOAL_LAST_RUN_KEY, today, clock, () -> {
            Map<Long, Long> reminders = new LinkedHashMap<>();
            for (var goal : studentProfileRepository.findDailyGoalsForReminder(
                    learnClock.startOf(today.minusDays(ACTIVE_DAYS - 1L)), learnClock.startOf(today),
                    learnClock.startOf(today.plusDays(1)), XpService.SOURCE_DAILY_GOAL, today.toEpochDay())) {
                reminders.put(goal.learnerId(), goal.remainingXp());
            }
            learnerNotifications.dailyGoalReminders(reminders);
            return reminders.size();
        });
    }

    /** The week's mistakes reminders, if due (from Monday 10:00); returns how many learners were reminded. */
    public int remindMistakesIfDue(Clock clock) {
        LocalDateTime now = learnClock.dateTimeAt(clock.instant());
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (now.toLocalDate().equals(monday) && now.toLocalTime().isBefore(MISTAKES_FROM)) {
            return 0;
        }
        return jobs.runOnce(MISTAKES_LAST_RUN_KEY, monday, clock, () -> {
            Map<Long, Long> reminders = new LinkedHashMap<>();
            for (LearningMistakeRepository.Waiting learner : mistakeRepository.findLearnersWithWaitingMistakes(MIN_MISTAKES)) {
                reminders.put(learner.userId(), learner.waiting());
            }
            learnerNotifications.mistakesToReviewReminders(reminders);
            return reminders.size();
        });
    }
}
