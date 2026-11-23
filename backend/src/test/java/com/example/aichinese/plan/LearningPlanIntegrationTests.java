package com.example.aichinese.plan;

import static com.example.aichinese.support.LearningApiTestClient.PLAN;
import static com.example.aichinese.support.LearningApiTestClient.SKIPPED_CHECK;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.LearningPlanStep;
import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LearningPlanStepRepository;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;

/**
 * The learning plan end to end: setup state, building from the level check (graded again by the server), the
 * rule-based plan when SparkBot is unavailable (the test profile has no AI provider), new goals and retaken checks
 * as new versions, Home, and who may read what.
 */
class LearningPlanIntegrationTests extends ApiIntegrationTest {

    private static final String NEXT = "/api/v1/students/level-check/next";

    @Autowired
    private LearningPlanRepository planRepository;

    @Autowired
    private LearningPlanStepRepository stepRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LearnLessonCompletionRepository completionRepository;

    @Autowired
    private LearnClock clock;

    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Autowired
    private LevelCheckQuestionRepository levelCheckQuestionRepository;

    @Test
    void aNewStudentStartsWithTheSetupAndMaySetItUpLater() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-new"));
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupState").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.firstTime").value(true))
                .andExpect(jsonPath("$.data.email.confirmed").value(false))
                .andExpect(jsonPath("$.data.next.unitNumber").value(1))
                .andExpect(jsonPath("$.data.next.character").isString())
                .andExpect(jsonPath("$.data.plan").doesNotExist());

        mockMvc.perform(api.authPost(PLAN + "/skip", token))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.setupState").value("SKIPPED"));
        mockMvc.perform(api.authGet(PLAN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").doesNotExist());
    }

    @Test
    void aBrandNewStudentSkipsTheCheckAndGetsARuleBasedPlanFromUnitOne() throws Exception {
        String email = uniqueEmail("plan-brand-new");
        String token = api.registerStudent(email);
        JsonNode plan = api.startPlan(token, goal("TRAVEL", List.of("ORDER_FOOD", "ASK_DIRECTIONS"), "HOURS_2_PLUS",
                List.of("LISTENING")), SKIPPED_CHECK);

        assertThat(plan.path("aiAssisted").asBoolean()).isFalse();
        assertThat(plan.path("goal").path("title").isNull()).isTrue();
        assertThat(plan.path("level").path("placed").asText()).isEqualTo("NEW_TO_MANDARIN");
        Long studentId = studentProfile(email).getId();
        assertThat(plansOf(studentId))
                .singleElement()
                .satisfies(saved -> assertThat(saved.getLevelCheckedAt()).isNull());
        JsonNode first = plan.path("steps").get(0);
        assertThat(first.path("kind").asText()).isEqualTo("LEARN_UNIT");
        assertThat(first.path("unitNumber").asInt()).isEqualTo(1);
        assertThat(first.path("status").asText()).isEqualTo("NEXT");
        assertThat(first.path("quickReview").asBoolean()).isFalse();
        // Ordering food matches Unit 3 ("Want") and asking the way matches Unit 4 ("Question words").
        List<Integer> units = learnUnitNumbers(plan);
        assertThat(units).startsWith(1, 2, 3, 4);
        assertThat(units).isSorted();
        assertThat(plan.path("weeks").path("current").asInt()).isEqualTo(1);
        assertThat(plan.path("progress").path("status").asText()).isEqualTo("STARTS_TODAY");
        assertThat(plan.path("thisWeek").findValuesAsText("kind")).contains("LEARN_NEXT", "XIAOMEI");

        // The daily time leaves the daily goal (an Account setting) at a new learner's 20 XP; the setup is done;
        // SparkBot's unavailability was logged under Learning plans.
        assertThat(plan.path("pace").path("key").asText()).isEqualTo("HOURS_2_PLUS");
        assertThat(plan.path("pace").path("minMinutesPerDay").asInt()).isEqualTo(120);
        assertThat(plan.path("pace").path("maxMinutesPerDay").isNull()).isTrue();
        mockMvc.perform(api.authGet("/api/v1/students/daily-goal", token))
                .andExpect(jsonPath("$.data.dailyGoalXp").value(20));
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.setupState").value("COMPLETED"))
                .andExpect(jsonPath("$.data.plan.week").value(1));
        Long userId = user(email).getId();
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_logs where user_id = ? and feature_key = ?",
                Long.class, userId, AiFeatureKey.LEARNING_PLAN.name())).isOne();
    }

    @Test
    void theServerGradesTheLevelCheckAndAStrongResultStartsWithAQuickReview() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-strong"));
        List<Map<String, Object>> answers = finishCheck(token, "BEGINNER_2");
        JsonNode plan = api.startPlan(token, goal("WORK", List.of("GREET_CUSTOMERS"), "HOURS_1_TO_2", List.of()),
                Map.of("selfLevel", "THE_BASICS", "skipped", false, "answers", answers));

        assertThat(plan.path("level").path("placed").asText()).isEqualTo("BEGINNER_2");
        assertThat(plan.path("level").path("target").asText()).isEqualTo("BEGINNER_2");
        // Level 1 units are a quick review; the plan reaches the first Beginner 2 unit, which is new.
        List<JsonNode> learnSteps = learnSteps(plan).toList();
        assertThat(learnSteps.subList(0, learnSteps.size() - 1))
                .isNotEmpty()
                .allMatch(step -> step.path("quickReview").asBoolean());
        assertThat(learnSteps.getLast().path("quickReview").asBoolean()).isFalse();
    }

    @Test
    void anUnfinishedCheckOrASkippedCheckThatIsNotBrandNewIsRejected() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-unfinished"));
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of()),
                        "levelCheck", Map.of("selfLevel", "A_LITTLE", "skipped", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Finish the level check first."));

        JsonNode first = next(token, "BEGINNER_1", List.of());
        long questionId = first.path("next").path("id").asLong();
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of()),
                        "levelCheck", Map.of("selfLevel", "A_LITTLE", "skipped", false,
                                "answers", List.of(levelCheckAnswer(questionId, rightLevelCheckOption(questionId)))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Finish the level check first."));
    }

    @Test
    void anEmptyCheckIsRefusedWhileQuestionsArePublished() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-empty-check"));
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of()),
                        "levelCheck", Map.of("selfLevel", "A_LITTLE", "skipped", false, "answers", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Finish the level check first."));

        api.startTravelPlan(token);
        mockMvc.perform(api.jsonPost(PLAN + "/level-check", token, Map.of("answers", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Finish the level check first."));
    }

    @Test
    void withNoPublishedQuestionTheCheckPlacesTheStudentWhereItStarts() throws Exception {
        String email = uniqueEmail("plan-no-questions");
        String token = api.registerStudent(email);
        try (var ignored = hidePublishedQuestions()) {
            // Nothing to ask: the check is over at once, at the level it starts from.
            JsonNode step = next(token, "BEGINNER_2", List.of());
            assertThat(step.path("done").asBoolean()).isTrue();
            assertThat(step.path("result").path("level").asText()).isEqualTo("BEGINNER_2");
            assertThat(step.path("answeredCount").asInt()).isZero();

            // "The basics" starts the check at Beginner 2, so the plan is built there.
            JsonNode plan = api.startPlan(token, goal("WORK", List.of("GREET_CUSTOMERS"), "HOURS_1_TO_2", List.of()),
                    Map.of("selfLevel", "THE_BASICS", "skipped", false, "answers", List.of()));
            assertThat(plan.path("level").path("placed").asText()).isEqualTo("BEGINNER_2");

            // A retake starts at the plan's level; with nothing asked, the plan and its check stay as they are.
            LearningPlan before = activePlan(email);
            JsonNode retake = api.data(mockMvc.perform(api.jsonPost(PLAN + "/level-check", token,
                            Map.of("answers", List.of())))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(retake.path("level").asText()).isEqualTo("BEGINNER_2");
            assertThat(retake.path("changed").asBoolean()).isFalse();
            LearningPlan after = activePlan(email);
            assertThat(after.getId()).isEqualTo(before.getId());
            assertThat(after.getLevelCheckedAt()).isEqualTo(before.getLevelCheckedAt());
        }
    }

    /** A retake with nothing to ask leaves the plan as it stands: its level and the skills of its last check. */
    @Test
    void aRetakeWithNoPublishedQuestionKeepsThePlansOwnSkills() throws Exception {
        String email = uniqueEmail("plan-retake-skills");
        String token = api.registerStudent(email);
        // A real check first: "don't know" and right answers in turn, so the plan has known and new skills.
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode step = next(token, "BEGINNER_1", answers);
        while (!step.path("done").asBoolean()) {
            long questionId = step.path("next").path("id").asLong();
            Long option = answers.size() % 2 == 0 ? null : rightLevelCheckOption(questionId);
            answers.add(levelCheckAnswer(questionId, option));
            step = next(token, "BEGINNER_1", answers);
        }
        JsonNode built = api.startPlan(token, goal("TRAVEL", List.of("ORDER_FOOD"), "HOURS_1_TO_2", List.of()),
                Map.of("selfLevel", "A_LITTLE", "skipped", false, "answers", answers));
        JsonNode level = built.path("level");
        assertThat(level.path("knownSkills").size()).isPositive();
        assertThat(level.path("newSkills").size()).isPositive();

        try (var ignored = hidePublishedQuestions()) {
            JsonNode retake = api.data(mockMvc.perform(api.jsonPost(PLAN + "/level-check", token,
                            Map.of("answers", List.of())))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(retake.path("level").asText()).isEqualTo(level.path("placed").asText());
            assertThat(retake.path("changed").asBoolean()).isFalse();
            JsonNode kept = api.data(mockMvc.perform(api.authGet(PLAN, token)).andReturn()).path("level");
            assertThat(kept.path("knownSkills")).isEqualTo(level.path("knownSkills"));
            assertThat(kept.path("newSkills")).isEqualTo(level.path("newSkills"));
        }
    }

    @Test
    void theGoalIsValidated() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-invalid"));

        Map<String, Object> past = goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of());
        past.put("deadline", clock.today().minusDays(1).toString());
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", past, "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick a date in the future, within 2 years."));

        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of("LISTENING", "SPEAKING", "READING")),
                        "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isBadRequest());

        Map<String, Object> blankOtherReason = goal("OTHER", List.of(), "UNDER_30_MIN", List.of());
        blankOtherReason.put("reasonText", " ");
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", blankOtherReason,
                        "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tell us your reason in your own words."));

        Map<String, Object> longWords = goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of());
        longWords.put("goalText", "x".repeat(201));
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", longWords, "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAmbitiousDeadlineGetsAFirstMilestone() throws Exception {
        String email = uniqueEmail("plan-ambitious");
        String token = api.registerStudent(email);
        String deadline = clock.today().plusDays(10).toString();
        Map<String, Object> goal = goal("TRAVEL",
                List.of("ORDER_FOOD", "ASK_DIRECTIONS", "GO_SHOPPING"), "UNDER_30_MIN", List.of());
        goal.put("deadline", deadline);
        JsonNode plan = api.startPlan(token, goal, SKIPPED_CHECK);

        assertThat(plan.path("milestone").path("date").asText()).isEqualTo(deadline);
        assertThat(plan.path("milestone").path("stepPositions").size()).isPositive();
        Long studentId = studentProfile(email).getId();
        assertThat(plansOf(studentId))
                .singleElement()
                .satisfies(saved -> assertThat(saved.getMilestoneWeek()).isEqualTo(2));
        // 10 days are 2 calendar weeks; the Learn steps after the milestone come later.
        assertThat(plan.path("weeks").path("total").asInt()).isEqualTo(2);
        assertThat(plan.path("weeks").path("content").asInt()).isGreaterThan(2);
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.plan.milestoneDate").value(deadline))
                .andExpect(jsonPath("$.data.plan.totalWeeks").value(2));
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.plan.milestoneDate").value(deadline));
    }

    /**
     * Setup saves a draft that "Edit goal" can rebuild. Home and My learning plan see it after "Start my path".
     * Starting it twice keeps the same plan and daily XP goal.
     */
    @Test
    void theSetupsDraftBecomesActiveOnlyWhenTheLearnerStartsIt() throws Exception {
        String email = uniqueEmail("plan-confirm");
        String token = api.registerStudent(email);
        JsonNode draft = api.data(mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of("ORDER_FOOD"), "HOURS_1_TO_2", List.of()), "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(draft.path("goal").path("reason").asText()).isEqualTo("TRAVEL");
        mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(api.authGet(PLAN + "/overview", token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.plan").doesNotExist());
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.setupState").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.plan").doesNotExist());

        // "Edit goal" before starting: the draft is rebuilt, still waiting.
        JsonNode edited = api.data(mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of(
                        "goal", goal("WORK", List.of("GREET_CUSTOMERS"), "MINUTES_30_TO_60", List.of()))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(edited.path("goal").path("reason").asText()).isEqualTo("WORK");
        assertThat(edited.path("pace").path("key").asText()).isEqualTo("MINUTES_30_TO_60");
        Long studentId = studentProfile(email).getId();
        assertThat(plansOf(studentId))
                .singleElement()
                .satisfies(plan -> {
                    assertThat(plan.isConfirmed()).isFalse();
                    assertThat(plan.isActive()).isFalse();
                });
        mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

        JsonNode started = api.data(mockMvc.perform(api.authPost(PLAN + "/confirm", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(started.path("goal").path("reason").asText()).isEqualTo("WORK");
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.setupState").value("COMPLETED"))
                .andExpect(jsonPath("$.data.plan.reason").value("WORK"));
        mockMvc.perform(api.authPost(PLAN + "/confirm", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goal.reason").value("WORK"));
        assertThat(activePlan(email).isConfirmed()).isTrue();
        mockMvc.perform(api.authGet("/api/v1/students/daily-goal", token))
                .andExpect(jsonPath("$.data.dailyGoalXp").value(20));
    }

    /** "Set up later" after the plan was built removes it; there is then nothing to start. */
    @Test
    void settingUpLaterAfterBuildingDiscardsThePlan() throws Exception {
        String email = uniqueEmail("plan-discard");
        String token = api.registerStudent(email);
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of("ORDER_FOOD"), "UNDER_30_MIN", List.of()),
                        "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isOk());

        mockMvc.perform(api.authPost(PLAN + "/skip", token)).andExpect(status().isOk());

        Long studentId = studentProfile(email).getId();
        assertThat(plansOf(studentId))
                .isEmpty();
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.setupState").value("SKIPPED"));
        mockMvc.perform(api.authPost(PLAN + "/confirm", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Build your learning plan first."));
    }

    @Test
    void aPlanBuiltMoreThanADayAgoIsNotStarted() throws Exception {
        String email = uniqueEmail("plan-stale");
        String token = api.registerStudent(email);
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of("ORDER_FOOD"), "UNDER_30_MIN", List.of()),
                        "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isOk());
        Long studentId = studentProfile(email).getId();
        LearningPlan draft = planRepository.findFirstByStudentIdAndConfirmedFalseOrderByVersionDesc(studentId)
                .orElseThrow();
        // Bound as a Timestamp, as Hibernate binds it (created_at is not updatable through the entity).
        jdbc.update("update learning_plans set created_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(2)), draft.getId());

        mockMvc.perform(api.authPost(PLAN + "/confirm", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This plan was built more than a day ago. Build it again."));
        mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * Days of this plan week before today without the daily goal are study days behind (Home: "You're 2 study days
     * behind"); the plan is then behind.
     */
    @Test
    void daysOfThisPlanWeekWithoutTheDailyGoalAreDaysBehind() throws Exception {
        String email = uniqueEmail("plan-behind");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        LearningPlan saved = activePlan(email);
        // The second plan week, which began 3 days ago; the goal was reached the day before yesterday only.
        LocalDate today = clock.today();
        saved.setStartsOn(today.minusDays(10));
        planRepository.save(saved);
        PointsTransaction goal = new PointsTransaction();
        goal.setStudent(studentProfile(email));
        goal.setPointsDelta(5);
        goal.setReason("Daily goal reached");
        goal.setSourceType(XpService.SOURCE_DAILY_GOAL);
        goal.setSourceId(today.minusDays(2).toEpochDay());
        pointsTransactionRepository.save(goal);

        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.plan.week").value(2))
                .andExpect(jsonPath("$.data.plan.daysBehind").value(2))
                .andExpect(jsonPath("$.data.plan.status").value("BEHIND"));
        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());
        assertThat(plan.path("progress").path("daysThisWeek").asInt()).isEqualTo(1);
        assertThat(plan.path("progress").path("daysBehind").asInt()).isEqualTo(2);
    }

    @Test
    void aPlanStillOpensWhenAPlatformZoneChangeMovesTodayBeforeItsStart() throws Exception {
        String email = uniqueEmail("plan-day-before-start");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        LearningPlan plan = activePlan(email);
        plan.setStartsOn(clock.today().plusDays(1));
        planRepository.save(plan);

        mockMvc.perform(api.authGet(PLAN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks.current").value(1))
                .andExpect(jsonPath("$.data.progress.daysThisWeek").value(0))
                .andExpect(jsonPath("$.data.progress.daysBehind").value(0));
    }

    @Test
    void aNewGoalOrAChangedLevelMakesANewVersionAndKeepsTheOldOne() throws Exception {
        String email = uniqueEmail("plan-versions");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);

        JsonNode updated = api.data(mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of(
                        "goal", goal("WORK", List.of("GREET_CUSTOMERS", "SMALL_TALK"), "MINUTES_30_TO_60", List.of("SPEAKING")))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updated.path("goal").path("reason").asText()).isEqualTo("WORK");
        assertThat(updated.path("level").path("placed").asText()).isEqualTo("NEW_TO_MANDARIN");
        Long studentId = studentProfile(email).getId();
        assertThat(plansOf(studentId))
                .hasSize(2)
                .filteredOn(plan -> plan.isActive())
                .singleElement()
                .satisfies(plan -> assertThat(plan.getVersion()).isEqualTo(2));

        // Retake: every answer right from NEW_TO_MANDARIN places the student higher, so the plan is rebuilt.
        List<Map<String, Object>> answers = finishCheck(token, "NEW_TO_MANDARIN");
        JsonNode retake = api.data(mockMvc.perform(api.jsonPost(PLAN + "/level-check", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(retake.path("changed").asBoolean()).isTrue();
        assertThat(retake.path("level").asText()).isNotEqualTo("NEW_TO_MANDARIN");
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.plan.level").value(retake.path("level").asText()));

        // The same answers again: the level stays, so does the plan version.
        JsonNode again = api.data(mockMvc.perform(api.jsonPost(PLAN + "/level-check", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(again.path("changed").asBoolean()).isFalse();
        assertThat(plansOf(studentId))
                .hasSize(3);
    }

    @Test
    void deletingTheAccountRemovesTheGoalInTheStudentsOwnWords() throws Exception {
        String email = uniqueEmail("plan-delete");
        String token = api.registerStudent(email);
        Map<String, Object> goal = goal("TRAVEL", List.of("ORDER_FOOD"), "UNDER_30_MIN", List.of());
        goal.put("goalText", "Visiting my aunt in Ipoh next spring.");
        api.startPlan(token, goal, SKIPPED_CHECK);
        Long studentId = studentProfile(email).getId();
        // The test profile has no AI provider, so write what SparkBot and an "Other" reason would have stored.
        LearningPlan built = activePlan(email);
        built.setReasonText("Visiting family in Ipoh");
        built.setGoalTitle("Ipoh family visit");
        built.setGoalSummary("Prepare to chat and order food with your aunt in Ipoh.");
        built.setWorkOn("Food words for my aunt in Ipoh");
        built.setOutcomes("Order at my aunt's favourite stall");
        built.setMilestoneText("Ready for Ipoh");
        planRepository.save(built);
        List<LearningPlanStep> steps = stepRepository.findByPlanIdOrderByPositionAsc(built.getId());
        steps.forEach(step -> step.setReason("Useful when you visit your aunt."));
        stepRepository.saveAll(steps);

        mockMvc.perform(api.jsonPost("/api/v1/users/me/delete", token, Map.of("confirmEmail", email)))
                .andExpect(status().isOk());

        assertThat(plansOf(studentId))
                .singleElement()
                .satisfies(plan -> {
                    assertThat(plan.getGoalText()).isNull();
                    assertThat(plan.getReasonText()).isNull();
                    assertThat(plan.getGoalTitle()).isNull();
                    assertThat(plan.getGoalSummary()).isNull();
                    assertThat(plan.getWorkOn()).isNull();
                    assertThat(plan.getOutcomes()).isNull();
                    assertThat(plan.getMilestoneText()).isNull();
                    assertThat(plan.getReason().name()).isEqualTo("TRAVEL");
                });
        assertThat(steps).isNotEmpty();
        assertThat(stepRepository.findByPlanIdOrderByPositionAsc(built.getId()))
                .allSatisfy(step -> assertThat(step.getReason()).isNull());
    }

    @Test
    void retakingOrUpdatingNeedsAPlan() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-none"));
        mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of("goal", goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Build your learning plan first."));
        mockMvc.perform(api.jsonPost(PLAN + "/level-check", token, Map.of("answers", List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Build your learning plan first."));
    }

    @Test
    void classesComeFromTheCatalogueAndOnlyTheStudentReadsThePlan() throws Exception {
        String adminToken = api.adminToken();
        Long joinedClass = api.publishedClass(adminToken, "Food and Conversation Club").classId();
        String token = api.registerStudent(uniqueEmail("plan-classes"));
        api.join(token, joinedClass);
        String membership = "/api/v1/students/classes/catalogue/membership";
        Map<String, Object> goal = goal("EVERYDAY", List.of("ORDER_FOOD", "SHORT_CHATS"), "HOURS_1_TO_2", List.of());
        JsonNode draft = api.data(mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", goal, "levelCheck", SKIPPED_CHECK)))
                .andExpect(status().isOk()).andReturn());
        assertThat(draft.path("steps").valueStream()
                .filter(step -> "CLASS".equals(step.path("kind").asText()))
                .map(step -> step.path("refId").asLong())).contains(joinedClass);
        mockMvc.perform(api.authGet(membership, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedClassIds").isEmpty());

        JsonNode plan = api.data(mockMvc.perform(api.authPost(PLAN + "/confirm", token))
                .andExpect(status().isOk()).andReturn());
        JsonNode confirmed = api.data(mockMvc.perform(api.authGet(membership, token))
                .andExpect(status().isOk()).andReturn());
        assertThat(confirmed.path("plannedClassIds").valueStream().map(JsonNode::asLong)).contains(joinedClass);

        JsonNode classStep = plan.path("steps").valueStream()
                .filter(step -> step.path("kind").asText().equals("CLASS") && step.path("refId").asLong() == joinedClass)
                .findFirst()
                .orElseThrow();
        assertThat(classStep.path("status").asText()).isEqualTo("IN_MY_CLASSES");
        assertThat(classStep.path("joined").asBoolean()).isTrue();
        // Its next lesson, as My classes shows it ("Next: Meeting Classmates").
        assertThat(classStep.path("nextLesson").path("kind").asText()).isEqualTo("LESSON");
        assertThat(classStep.path("nextLesson").path("title").asText()).isEqualTo("Food and Conversation Club lesson");
        assertThat(classStep.path("goals").toString()).contains("SHORT_CHATS");

        mockMvc.perform(api.authGet(PLAN, adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void theClassPickedFirstStaysWithThePlanAndHomeShowsIt() throws Exception {
        String adminToken = api.adminToken();
        String marketTitle = "Pick-first Market Mandarin";
        String stationTitle = "Pick-first Culture Film Office Career";
        Long market = api.publishedClass(adminToken, marketTitle).classId();
        Long station = api.publishedClass(adminToken, stationTitle).classId();
        Long elsewhere = api.publishedClass(adminToken, "Pick-first Elsewhere").classId();
        String email = uniqueEmail("plan-pick");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        // The plan's optional classes are these two (other tests' classes are in the catalogue too).
        LearningPlan saved = activePlan(email);
        List<LearningPlanStep> steps = stepRepository.findByPlanIdOrderByPositionAsc(saved.getId());
        stepRepository.deleteAll(steps.stream().filter(step -> step.getStepKind() == PlanStepKind.CLASS).toList());
        int last = steps.stream().mapToInt(LearningPlanStep::getPosition).max().orElse(0);
        stepRepository.saveAll(List.of(classStep(saved, market, last + 1), classStep(saved, station, last + 2)));
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.plan.optionalClass.title").value(marketTitle));

        // "Pick this first" is kept with the plan, and Home names the picked class (S3.19/S3.20).
        mockMvc.perform(api.jsonPut(PLAN + "/class-pick", token, Map.of("classId", station)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickedClassId").value(station));
        mockMvc.perform(api.authGet(PLAN, token))
                .andExpect(jsonPath("$.data.pickedClassId").value(station));
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.plan.optionalClass.title").value(stationTitle));

        // Only a class of the plan can be picked; the pick stays as it was.
        mockMvc.perform(api.jsonPut(PLAN + "/class-pick", token, Map.of("classId", elsewhere)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick a class from your plan."));
        assertThat(activePlan(email).getPickedClassId()).isEqualTo(station);

        // No pick: the plan's own order again.
        mockMvc.perform(api.jsonPut(PLAN + "/class-pick", token, singletonMap("classId", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickedClassId").doesNotExist());
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.plan.optionalClass.title").value(marketTitle));

        // A rebuilt plan keeps the pick while the class is still in it, and drops it once the class leaves the plan.
        mockMvc.perform(api.jsonPut(PLAN + "/class-pick", token, Map.of("classId", station)))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPut(PLAN + "/goal", token,
                        Map.of("goal", goal("WORK", List.of("ENJOY_MEDIA", "TALK_ABOUT_WORK"), "MINUTES_30_TO_60", List.of()))))
                .andExpect(status().isOk());
        LearningPlan kept = activePlan(email);
        assertThat(kept.getVersion()).isEqualTo(saved.getVersion() + 1);
        assertThat(stepRepository.findByPlanIdOrderByPositionAsc(kept.getId()))
                .anyMatch(step -> step.getStepKind() == PlanStepKind.CLASS && station.equals(step.getRefId()));
        assertThat(kept.getPickedClassId()).isEqualTo(station);

        mockMvc.perform(api.jsonPut(PLAN + "/goal", token,
                        Map.of("goal", goal("TRAVEL", List.of("ORDER_FOOD"), "MINUTES_30_TO_60", List.of()))))
                .andExpect(status().isOk());
        LearningPlan dropped = activePlan(email);
        assertThat(dropped.getVersion()).isEqualTo(kept.getVersion() + 1);
        assertThat(stepRepository.findByPlanIdOrderByPositionAsc(dropped.getId()))
                .noneMatch(step -> step.getStepKind() == PlanStepKind.CLASS && station.equals(step.getRefId()));
        assertThat(dropped.getPickedClassId()).isNull();
    }

    @Test
    void aClassCanBePickedOnlyWithAPlan() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-pick-none"));
        mockMvc.perform(api.jsonPut(PLAN + "/class-pick", token, Map.of("classId", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Build your learning plan first."));
    }

    private static LearningPlanStep classStep(LearningPlan plan, Long classId, int position) {
        LearningPlanStep step = new LearningPlanStep();
        step.setPlan(plan);
        step.setPosition(position);
        step.setStepKind(PlanStepKind.CLASS);
        step.setRefId(classId);
        step.setTitle("Class " + classId);
        step.setGoalPicks("ORDER_FOOD");
        step.setWeekFrom(2);
        step.setWeekTo(3);
        return step;
    }

    @Test
    void aNameRetiredSinceThePlanWasSavedIsPassedOverAndThePlanStillOpens() throws Exception {
        String email = uniqueEmail("plan-retired");
        String token = api.registerStudent(email);
        api.startPlan(token, goal("TRAVEL", List.of("ORDER_FOOD"), "HOURS_1_TO_2", List.of("LISTENING")),
                SKIPPED_CHECK);
        LearningPlan saved = activePlan(email);
        saved.setFocusSkills("RETIRED_FOCUS,LISTENING");
        saved.setGoalPicks("RETIRED_GOAL,ORDER_FOOD");
        saved.setKnownSkills("RETIRED_SKILL");
        planRepository.save(saved);
        List<LearningPlanStep> steps = stepRepository.findByPlanIdOrderByPositionAsc(saved.getId());
        steps.forEach(step -> step.setGoalPicks("RETIRED_GOAL,ORDER_FOOD"));
        stepRepository.saveAll(steps);

        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());

        assertThat(plan.path("goal").path("focus").toString()).isEqualTo("[\"LISTENING\"]");
        assertThat(plan.path("goal").path("picks").toString()).isEqualTo("[\"ORDER_FOOD\"]");
        assertThat(plan.path("level").path("knownSkills").toString()).isEqualTo("[]");
        assertThat(plan.path("steps").get(0).path("goals").toString()).isEqualTo("[\"ORDER_FOOD\"]");
    }

    @Test
    void aPlanPastItsEndKeepsASevenDayWeek() throws Exception {
        String email = uniqueEmail("plan-past-end");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        LearningPlan saved = activePlan(email);
        // Ten days past the last plan week: the current 7-day week began 3 days ago, not when the last plan week did.
        saved.setStartsOn(clock.today().minusDays(saved.getTotalWeeks() * 7L + 10));
        planRepository.save(saved);
        List<LearnLesson> lessons = learnLessonRepository.findAll().stream()
                .filter(lesson -> lesson.getLessonKind() == LearnLessonKind.LESSON)
                .limit(2)
                .toList();
        StudentProfile student = studentProfile(email);
        LocalDateTime now = LocalDateTime.now();
        completionRepository.saveAll(List.of(
                completion(student, lessons.get(0), now.minusDays(10)),
                completion(student, lessons.get(1), now.minusDays(1))));

        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());

        assertThat(plan.path("weeks").path("current").asInt()).isEqualTo(saved.getTotalWeeks());
        assertThat(plan.path("thisWeek").valueStream()
                .filter(task -> task.path("kind").asText().equals("LEARN_DONE"))
                .map(task -> task.path("title").asText()))
                .containsExactly(lessons.get(1).getTitle());
    }

    /** This week's done list holds the lessons and reviews the plan's progress counts, not an opened bonus chest. */
    @Test
    void thisWeeksDoneListLeavesOutAnOpenedBonusChest() throws Exception {
        String email = uniqueEmail("plan-week-chest");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        LearningPlan saved = activePlan(email);
        saved.setStartsOn(clock.today().minusDays(3));
        planRepository.save(saved);
        LearnLesson lesson = firstStopOfKind(LearnLessonKind.LESSON);
        LearnLesson chest = firstStopOfKind(LearnLessonKind.BONUS);
        StudentProfile student = studentProfile(email);
        LocalDateTime now = LocalDateTime.now();
        completionRepository.saveAll(List.of(
                completion(student, lesson, now.minusHours(2)),
                completion(student, chest, now.minusHours(1))));

        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());

        assertThat(plan.path("thisWeek").valueStream()
                .filter(task -> task.path("kind").asText().equals("LEARN_DONE"))
                .map(task -> task.path("title").asText()))
                .containsExactly(lesson.getTitle());
    }

    private LearnLesson firstStopOfKind(LearnLessonKind kind) {
        return learnLessonRepository.findAll().stream()
                .filter(stop -> stop.getLessonKind() == kind)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void anExpiredOneWeekPlanDoesNotSayItStartsToday() throws Exception {
        String email = uniqueEmail("plan-one-week");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        LearningPlan saved = activePlan(email);
        saved.setTotalWeeks(1);
        saved.setContentWeeks(1);
        saved.setStartsOn(clock.today().minusDays(10));
        planRepository.save(saved);

        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());

        assertThat(plan.path("weeks").path("current").asInt()).isEqualTo(1);
        assertThat(plan.path("progress").path("status").asText()).isEqualTo("BEHIND");
    }

    /**
     * Every lesson of Level 1 done: its units are done and the level checkpoint, the gate to Level 2, is the path's next
     * step. A plan whose units are all done is complete, and this week's to-dos still lead to the checkpoint (Home says
     * it is past the plan). A plan built now starts behind the checkpoint: its first step offers the checkpoint, and so
     * do this week's to-dos and Home, shown with its level.
     */
    @Test
    void anOpenCheckpointIsThePlansNextStepOnceEveryLessonOfTheLevelIsDone() throws Exception {
        String email = uniqueEmail("plan-checkpoint");
        String token = api.registerStudent(email);
        Map<String, Object> goal = goal("TRAVEL", List.of("ORDER_FOOD"), "HOURS_1_TO_2", List.of());
        // Units of Level 1 only: ordering food matches Unit 3.
        assertThat(learnUnitNumbers(api.startPlan(token, goal, SKIPPED_CHECK))).isNotEmpty().allSatisfy(unit ->
                assertThat(unit).isLessThanOrEqualTo(4));
        JsonNode levelOne = learnPath(token).path("levels").get(0);
        long checkpointId = levelOne.path("checkpoint").path("id").asLong();
        List<Long> lessonIds = levelOne.path("units").valueStream().flatMap(unit -> unit.path("lessons").valueStream())
                .map(stop -> stop.path("id").asLong()).toList();
        finishLessons(email, lessonIds, LocalDateTime.now().minusDays(1));
        JsonNode done = learnPath(token).path("levels").get(0);
        assertThat(done.path("units").valueStream().map(unit -> unit.path("state").asText())).containsOnly("DONE");
        assertThat(done.path("checkpoint").path("state").asText()).isEqualTo("AVAILABLE");

        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());
        assertThat(learnSteps(plan).map(step -> step.path("status").asText())).containsOnly("DONE");
        assertThat(plan.path("progress").path("status").asText()).isEqualTo("COMPLETE");
        assertThat(learnNextTasks(plan)).containsExactly(checkpointId);
        JsonNode home = api.data(mockMvc.perform(api.authGet("/api/v1/students/home", token)).andReturn()).path("next");
        assertThat(home.path("kind").asText()).isEqualTo("CHECKPOINT");
        assertThat(home.path("lessonId").asLong()).isEqualTo(checkpointId);
        assertThat(home.path("inPlan").asBoolean()).isFalse();
        // Shown with its level: Level 1's four units, all done.
        assertThat(home.path("level").asInt()).isEqualTo(1);
        assertThat(home.path("stops").valueStream().map(stop -> stop.path("position").asInt())).containsExactly(1, 2, 3, 4);
        assertThat(home.path("stops").valueStream().map(stop -> stop.path("state").asText())).containsOnly("DONE");

        JsonNode rebuilt = api.data(mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of("goal", goal)))
                .andExpect(status().isOk()).andReturn());
        JsonNode current = learnSteps(rebuilt).findFirst().orElseThrow();
        assertThat(current.path("unitNumber").asInt()).isEqualTo(5);
        assertThat(current.path("status").asText()).isEqualTo("NEXT");
        assertThat(current.path("nextLesson").path("id").asLong()).isEqualTo(checkpointId);
        assertThat(current.path("nextLesson").path("kind").asText()).isEqualTo("CHECKPOINT");
        assertThat(learnNextTasks(rebuilt)).containsExactly(checkpointId);
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.next.kind").value("CHECKPOINT"))
                .andExpect(jsonPath("$.data.next.lessonId").value(checkpointId))
                .andExpect(jsonPath("$.data.next.inPlan").value(true));
    }

    private static List<Long> learnNextTasks(JsonNode plan) {
        return plan.path("thisWeek").valueStream()
                .filter(task -> task.path("kind").asText().equals("LEARN_NEXT"))
                .map(task -> task.path("lessonId").asLong())
                .toList();
    }

    /** A unit is done when its required stops are; its bonus chest is optional, so the plan can be complete without it. */
    @Test
    void aPlanWhoseUnitsAreAllDoneIsCompleteWithItsBonusChestsUnopened() throws Exception {
        String email = uniqueEmail("plan-complete");
        String token = api.registerStudent(email);
        JsonNode built = api.startTravelPlan(token);
        List<Long> planUnits = learnSteps(built).map(step -> step.path("refId").asLong()).toList();
        List<JsonNode> units = learnPath(token).path("levels").valueStream()
                .flatMap(level -> level.path("units").valueStream())
                .filter(unit -> planUnits.contains(unit.path("id").asLong()))
                .toList();
        assertThat(units).hasSameSizeAs(planUnits);
        // The plan's units have bonus chests, and they stay closed.
        List<JsonNode> stops = units.stream().flatMap(unit -> unit.path("lessons").valueStream()).toList();
        assertThat(stops).anyMatch(stop -> stop.path("kind").asText().equals("BONUS"));
        List<Long> lessonIds = stops.stream()
                .filter(stop -> !stop.path("kind").asText().equals("BONUS"))
                .map(stop -> stop.path("id").asLong()).toList();
        finishLessons(email, lessonIds, LocalDateTime.now().minusHours(1));

        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());

        assertThat(learnSteps(plan).map(step -> step.path("status").asText())).containsOnly("DONE");
        assertThat(plan.path("progress").path("percent").asInt()).isEqualTo(100);
        assertThat(plan.path("progress").path("status").asText()).isEqualTo("COMPLETE");
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.plan.percent").value(100))
                .andExpect(jsonPath("$.data.plan.status").value("COMPLETE"));
    }

    /**
     * A retake that changes the level rebuilds the plan from the stored goal; once its date is not after today, the
     * new version has no deadline (as "No deadline"), so no milestone dated in the past, on every page.
     */
    @Test
    void aRetakeRebuildsAPlanWhoseGoalDateHasPassedWithoutADeadline() throws Exception {
        String email = uniqueEmail("plan-date-passed");
        String token = api.registerStudent(email);
        Map<String, Object> goal = goal("TRAVEL", List.of("ORDER_FOOD", "ASK_DIRECTIONS"), "UNDER_30_MIN", List.of());
        goal.put("deadline", clock.today().plusDays(10).toString());
        JsonNode built = api.startPlan(token, goal, SKIPPED_CHECK);
        assertThat(built.path("milestone").isObject()).isTrue();
        LearningPlan saved = activePlan(email);
        saved.setDeadline(clock.today());
        planRepository.save(saved);

        List<Map<String, Object>> answers = finishCheck(token, "NEW_TO_MANDARIN");
        JsonNode retake = api.data(mockMvc.perform(api.jsonPost(PLAN + "/level-check", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(retake.path("changed").asBoolean()).isTrue();

        LearningPlan rebuilt = activePlan(email);
        assertThat(rebuilt.getVersion()).isEqualTo(2);
        assertThat(rebuilt.getDeadline()).isNull();
        assertThat(rebuilt.getMilestoneWeek()).isNull();
        assertThat(rebuilt.getTotalWeeks()).isEqualTo(rebuilt.getContentWeeks() + 1);
        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());
        assertThat(plan.path("goal").path("deadline").isNull()).isTrue();
        assertThat(plan.path("milestone").isNull()).isTrue();
        JsonNode overview = api.data(mockMvc.perform(api.authGet(PLAN + "/overview", token)).andReturn());
        assertThat(overview.path("plan").path("deadline").isNull()).isTrue();
        JsonNode home = api.data(mockMvc.perform(api.authGet("/api/v1/students/home", token)).andReturn());
        assertThat(home.path("plan").path("deadline").isNull()).isTrue();
    }

    /**
     * The road map's "N lessons done so far" counts lessons and unit reviews, as the Learn rail and the badges count
     * them: an opened bonus chest and a passed checkpoint are no lessons.
     */
    @Test
    void thePlansLessonsDoneSoFarCountOnlyLessonsAndReviews() throws Exception {
        String email = uniqueEmail("plan-kept");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        List<LearnLesson> path = learnLessonRepository.findPathInOrder();
        LocalDateTime completedAt = LocalDateTime.now().minusHours(1);
        StudentProfile student = studentProfile(email);
        List<LearnLessonCompletion> completions = new ArrayList<>();
        for (LearnLessonKind kind : LearnLessonKind.values()) {
            LearnLesson lesson = path.stream().filter(candidate -> candidate.getLessonKind() == kind)
                    .findFirst().orElseThrow();
            completions.add(completion(student, lesson, completedAt));
        }
        completionRepository.saveAll(completions);

        JsonNode plan = api.data(mockMvc.perform(api.authGet(PLAN, token)).andExpect(status().isOk()).andReturn());
        assertThat(plan.path("kept").path("lessons").asInt()).isEqualTo(2);
        assertThat(learnPath(token).path("completedLessons").asInt()).isEqualTo(2);
    }

    /**
     * The setup options and the plan say the platform's day, the one a goal date is checked against, so the pages count
     * their dates from it rather than from the browser's.
     */
    @Test
    void theOptionsAndThePlanSayThePlatformsDay() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-today"));
        mockMvc.perform(api.authGet(PLAN + "/options", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value(clock.today().toString()));
        JsonNode plan = api.startTravelPlan(token);
        assertThat(plan.path("today").asText()).isEqualTo(clock.today().toString());

        // The day itself has passed for a goal; the day after it is the earliest one.
        Map<String, Object> today = goal("TRAVEL", List.of(), "UNDER_30_MIN", List.of());
        today.put("deadline", clock.today().toString());
        mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of("goal", today, "language", "en")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick a date in the future, within 2 years."));
        Map<String, Object> tomorrow = new HashMap<>(today);
        tomorrow.put("deadline", clock.today().plusDays(1).toString());
        mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of("goal", tomorrow, "language", "en")))
                .andExpect(status().isOk());
    }

    /** Home's next lesson says whether its unit is one of the plan's: past the plan's last unit, it is on the path. */
    @Test
    void homeSaysWhenTheNextLessonIsPastThePlansUnits() throws Exception {
        String email = uniqueEmail("plan-past-units");
        String token = api.registerStudent(email);
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.next.inPlan").value(false));
        JsonNode built = api.startTravelPlan(token);
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.next.unitNumber").value(1))
                .andExpect(jsonPath("$.data.next.inPlan").value(true));

        List<Long> planUnits = learnSteps(built).map(step -> step.path("refId").asLong()).toList();
        List<Long> lessonIds = learnPath(token).path("levels").valueStream()
                .flatMap(level -> level.path("units").valueStream())
                .filter(unit -> planUnits.contains(unit.path("id").asLong()))
                .flatMap(unit -> unit.path("lessons").valueStream())
                .map(stop -> stop.path("id").asLong()).toList();
        finishLessons(email, lessonIds, LocalDateTime.now().minusHours(1));

        JsonNode home = api.data(mockMvc.perform(api.authGet("/api/v1/students/home", token)).andReturn());
        JsonNode next = home.path("next");
        assertThat(next.path("unitNumber").asInt()).isGreaterThan(learnUnitNumbers(built).getLast());
        assertThat(next.path("inPlan").asBoolean()).isFalse();
        assertThat(home.path("plan").isObject()).isTrue();
    }

    // Helpers

    private JsonNode learnPath(String token) throws Exception {
        return api.data(mockMvc.perform(api.authGet("/api/v1/students/learn/path", token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private LearningPlan activePlan(String email) {
        return planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(studentProfile(email).getId()).orElseThrow();
    }

    private List<LearningPlan> plansOf(Long studentId) {
        return entityManager.createQuery("select p from LearningPlan p where p.student.id = :student", LearningPlan.class)
                .setParameter("student", studentId).getResultList();
    }

    private void finishLessons(String email, List<Long> lessonIds, LocalDateTime when) {
        StudentProfile student = studentProfile(email);
        List<LearnLesson> lessons = learnLessonRepository.findAllById(lessonIds);
        assertThat(lessons).extracting(LearnLesson::getId).containsExactlyInAnyOrderElementsOf(lessonIds);
        completionRepository.saveAll(lessons.stream().map(lesson -> completion(student, lesson, when)).toList());
    }

    private AutoCloseable hidePublishedQuestions() {
        List<LevelCheckQuestion> published = levelCheckQuestionRepository.findPublishedOrAnswered(Set.of());
        published.forEach(question -> question.setPublishStatus(PublishStatus.DRAFT));
        levelCheckQuestionRepository.saveAll(published);
        return () -> {
            published.forEach(question -> question.setPublishStatus(PublishStatus.PUBLISHED));
            levelCheckQuestionRepository.saveAll(published);
        };
    }

    private static Map<String, Object> goal(String reason, List<String> goals, String pace, List<String> focus) {
        Map<String, Object> goal = new HashMap<>();
        goal.put("reason", reason);
        goal.put("goals", goals);
        goal.put("pace", pace);
        goal.put("focus", focus);
        return goal;
    }

    /** Answers the adaptive check to the end, every answer right. */
    private List<Map<String, Object>> finishCheck(String token, String startLevel) throws Exception {
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode step = next(token, startLevel, answers);
        while (!step.path("done").asBoolean()) {
            long questionId = step.path("next").path("id").asLong();
            answers.add(levelCheckAnswer(questionId, rightLevelCheckOption(questionId)));
            step = next(token, startLevel, answers);
        }
        return answers;
    }

    private JsonNode next(String token, String startLevel, List<Map<String, Object>> answers) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost(NEXT, token, Map.of("startLevel", startLevel, "answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static Stream<JsonNode> learnSteps(JsonNode plan) {
        return plan.path("steps").valueStream().filter(step -> step.path("kind").asText().equals("LEARN_UNIT"));
    }

    private static List<Integer> learnUnitNumbers(JsonNode plan) {
        return learnSteps(plan).map(step -> step.path("unitNumber").asInt()).toList();
    }
}
