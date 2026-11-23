package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.LearnerReminderJobs;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * The learners' scheduled reminders, run with a fixed clock on the platform's day (Asia/Singapore in the tests): the
 * evening daily-goal reminder and the weekly mistakes reminder, each once per period.
 */
class LearnerReminderJobsIntegrationTests extends ApiIntegrationTest {

    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Singapore");

    @Autowired
    private LearnerReminderJobs jobs;

    @Autowired
    private SystemSettingRepository settings;

    @Autowired
    private LearnClock clock;

    @BeforeEach
    @AfterEach
    void forgetTheLastRuns() {
        for (String key : List.of(LearnerReminderJobs.DAILY_GOAL_LAST_RUN_KEY, LearnerReminderJobs.MISTAKES_LAST_RUN_KEY)) {
            settings.findBySettingKey(key).ifPresent(settings::delete);
        }
    }

    @Test
    void theEveningReminderTellsALearnerWithAPlanHowMuchXpIsLeftOnceADay() throws Exception {
        String email = uniqueEmail("reminder-plan");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        String withoutPlan = uniqueEmail("reminder-no-plan");
        String otherToken = api.registerStudent(withoutPlan);
        // 10 XP each today, of the default 20 XP goal.
        reviewCards(token);
        reviewCards(otherToken);

        LocalDate today = clock.today();
        assertThat(jobs.remindDailyGoalIfDue(at(today, LocalTime.of(15, 0)))).isZero();
        assertThat(jobs.remindDailyGoalIfDue(at(today, LocalTime.of(19, 30)))).isPositive();

        assertThat(of(email, NotificationType.DAILY_GOAL_REMINDER)).singleElement().satisfies(reminder -> {
            assertThat(reminder.getTitle()).isEqualTo("Daily goal reminder");
            assertThat(reminder.getMessage()).isEqualTo("You're 10 XP away from today's goal.");
            assertThat(reminder.getTargetPath()).isEqualTo("/student/home");
        });
        assertThat(of(withoutPlan, NotificationType.DAILY_GOAL_REMINDER)).isEmpty();
        // Once a day, whatever the restarts.
        assertThat(jobs.remindDailyGoalIfDue(at(today, LocalTime.of(21, 0)))).isZero();
        assertThat(of(email, NotificationType.DAILY_GOAL_REMINDER)).hasSize(1);
    }

    @Test
    void theWeeklyReminderGoesToLearnersWithFiveMistakesOrMoreOnceAWeek() throws Exception {
        String email = uniqueEmail("reminder-mistakes");
        String token = api.registerStudent(email);
        String fewEmail = uniqueEmail("reminder-few-mistakes");
        String fewToken = api.registerStudent(fewEmail);
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        List<PracticeCatalog.Item> items = practiceCatalog.content(PracticeType.LISTENING, set).items();
        for (PracticeCatalog.Item item : items) {
            answerWrong(token, set, item);
        }
        answerWrong(fewToken, set, items.get(0));

        LocalDate monday = clock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(jobs.remindMistakesIfDue(at(monday, LocalTime.of(9, 0)))).isZero();
        assertThat(jobs.remindMistakesIfDue(at(monday, LocalTime.of(10, 30)))).isPositive();

        assertThat(of(email, NotificationType.MISTAKES_TO_REVIEW)).singleElement().satisfies(reminder -> {
            assertThat(reminder.getTitle()).isEqualTo("Mistakes to review");
            assertThat(reminder.getMessage())
                    .isEqualTo("You have 5 mistakes saved. Fixing them takes about 3 minutes.");
            assertThat(reminder.getTargetPath()).isEqualTo("/student/practice/mistakes");
        });
        assertThat(of(fewEmail, NotificationType.MISTAKES_TO_REVIEW)).isEmpty();
        // Once a week: later the same week nothing more, the next Monday again.
        assertThat(jobs.remindMistakesIfDue(at(monday.plusDays(2), LocalTime.NOON))).isZero();
        assertThat(jobs.remindMistakesIfDue(at(monday.plusWeeks(1), LocalTime.of(10, 30)))).isPositive();
        assertThat(of(email, NotificationType.MISTAKES_TO_REVIEW)).hasSize(2);
    }

    private void reviewCards(String token) throws Exception {
        PracticeActivityContent cards = practiceSet(PracticeType.FLASHCARDS, "Campus Words");
        mockMvc.perform(api.jsonPost("/api/v1/students/practice/flashcards/sets/" + cards.getId() + "/complete", token,
                        Map.of("answers", gotItRatings(cards))))
                .andExpect(status().isOk());
    }

    private void answerWrong(String token, PracticeActivityContent set, PracticeCatalog.Item item) throws Exception {
        mockMvc.perform(api.jsonPost("/api/v1/students/practice/listening/sets/" + set.getId() + "/check", token,
                        Map.of("itemKey", item.key(), "answer", wrongOption(item))))
                .andExpect(status().isOk());
    }

    private List<Notification> of(String email, NotificationType type) {
        return notificationsOf(email).stream().filter(notification -> notification.getNotificationType() == type).toList();
    }

    /** A clock at this time of a platform day. */
    private static Clock at(LocalDate day, LocalTime time) {
        return Clock.fixed(day.atTime(time).atZone(PLATFORM_ZONE).toInstant(), ZoneId.systemDefault());
    }
}
