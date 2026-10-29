package com.example.aichinese.plan;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LevelCheckOptionRepository;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * The learning plan end to end: setup state, building from the level check (graded again by the server), the
 * rule-based plan when SparkBot is unavailable (the test profile has no AI provider), new goals and retaken checks
 * as new versions, Home, and who may read what.
 */
class LearningPlanIntegrationTests extends ApiIntegrationTest {

    private static final String PLAN = "/api/v1/students/plan";
    private static final String NEXT = "/api/v1/students/level-check/next";

    @Autowired
    private LevelCheckOptionRepository optionRepository;

    @Autowired
    private LearningPlanRepository planRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupState").value("SKIPPED"));
        mockMvc.perform(api.authGet(PLAN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.setupState").value("SKIPPED"))
                .andExpect(jsonPath("$.data.plan").doesNotExist());
    }

    @Test
    void aBrandNewStudentSkipsTheCheckAndGetsARuleBasedPlanFromUnitOne() throws Exception {
        String email = uniqueEmail("plan-brand-new");
        String token = api.registerStudent(email);
        JsonNode plan = build(token, goal("TRAVEL", List.of("ORDER_FOOD", "ASK_DIRECTIONS"), "INTENSE",
                List.of("LISTENING")), Map.of("selfLevel", "NEW_TO_MANDARIN", "skipped", true));

        assertThat(plan.path("aiAssisted").asBoolean()).isFalse();
        assertThat(plan.path("goal").path("title").isNull()).isTrue();
        assertThat(plan.path("level").path("placed").asText()).isEqualTo("NEW_TO_MANDARIN");
        assertThat(plan.path("level").path("checkedAt").isNull()).isTrue();
        JsonNode first = plan.path("steps").get(0);
        assertThat(first.path("kind").asText()).isEqualTo("LEARN_UNIT");
        assertThat(first.path("unitNumber").asInt()).isEqualTo(1);
        assertThat(first.path("status").asText()).isEqualTo("NEXT");
        assertThat(first.path("quickReview").asBoolean()).isFalse();
        // Ordering food is in Unit 2 and asking the way in Unit 3: the path goes at least that far, in order.
        List<Integer> units = learnUnitNumbers(plan);
        assertThat(units).startsWith(1, 2, 3);
        assertThat(units).isSorted();
        assertThat(plan.path("coverage").findValuesAsText("goal")).containsExactly("ORDER_FOOD", "ASK_DIRECTIONS");
        assertThat(plan.path("coverage").findValues("learn")).allMatch(JsonNode::asBoolean);
        assertThat(plan.path("weeks").path("current").asInt()).isEqualTo(1);
        assertThat(plan.path("progress").path("status").asText()).isEqualTo("STARTS_TODAY");
        assertThat(plan.path("thisWeek").findValuesAsText("kind")).contains("LEARN_NEXT", "XIAOMEI");

        // The pace set the daily goal; the setup is done; SparkBot's unavailability was logged under Learning plans.
        mockMvc.perform(api.authGet("/api/v1/students/daily-goal", token))
                .andExpect(jsonPath("$.data.dailyGoalXp").value(30));
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.setupState").value("COMPLETED"))
                .andExpect(jsonPath("$.data.plan.week").value(1))
                .andExpect(jsonPath("$.data.plan.goalUnit.number").value(units.get(units.size() - 1)));
        Long userId = user(email).getId();
        assertThat(aiUsageLogRepository.findAll().stream()
                .filter(log -> log.getUser().getId().equals(userId))
                .filter(log -> log.getFeatureKey() == AiFeatureKey.LEARNING_PLAN)
                .count()).isEqualTo(1);
    }

    @Test
    void theServerGradesTheLevelCheckAndAStrongResultStartsWithAQuickReview() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-strong"));
        List<Map<String, Object>> answers = finishCheck(token, "BEGINNER_2", true);
        JsonNode plan = build(token, goal("WORK", List.of("GREET_CUSTOMERS"), "REGULAR", List.of()),
                Map.of("selfLevel", "THE_BASICS", "skipped", false, "answers", answers));

        assertThat(plan.path("level").path("placed").asText()).isEqualTo("BEGINNER_2");
        assertThat(plan.path("level").path("headStart").asBoolean()).isTrue();
        assertThat(plan.path("level").path("target").asText()).isEqualTo("BEGINNER_2");
        // Level 1 units are a quick review; the plan reaches the first Beginner 2 unit.
        JsonNode steps = plan.path("steps");
        assertThat(stream(steps).filter(step -> step.path("hskLevel").asInt() == 1))
                .allMatch(step -> step.path("quickReview").asBoolean());
        assertThat(stream(steps).anyMatch(step -> step.path("hskLevel").asInt() == 2)).isTrue();
    }

    @Test
    void anUnfinishedCheckOrASkippedCheckThatIsNotBrandNewIsRejected() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-unfinished"));
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of(), "LIGHT", List.of()),
                        "levelCheck", Map.of("selfLevel", "A_LITTLE", "skipped", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Finish the level check first."));

        JsonNode first = next(token, "BEGINNER_1", List.of());
        long questionId = first.path("next").path("id").asLong();
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of(), "LIGHT", List.of()),
                        "levelCheck", Map.of("selfLevel", "A_LITTLE", "skipped", false,
                                "answers", List.of(answer(questionId, rightOption(questionId)))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Finish the level check first."));
    }

    @Test
    void theGoalIsValidated() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-invalid"));
        Map<String, Object> brandNew = Map.of("selfLevel", "NEW_TO_MANDARIN", "skipped", true);

        Map<String, Object> past = new HashMap<>(goal("TRAVEL", List.of(), "LIGHT", List.of()));
        past.put("deadline", LocalDate.now().minusDays(1).toString());
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", past, "levelCheck", brandNew)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick a date in the future, within 2 years."));

        mockMvc.perform(api.jsonPost(PLAN, token, Map.of(
                        "goal", goal("TRAVEL", List.of(), "LIGHT", List.of("LISTENING", "SPEAKING", "READING")),
                        "levelCheck", brandNew)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", goal("OTHER", List.of(), "LIGHT", List.of()),
                        "levelCheck", brandNew)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tell us your reason in your own words."));

        Map<String, Object> longWords = new HashMap<>(goal("TRAVEL", List.of(), "LIGHT", List.of()));
        longWords.put("goalText", "x".repeat(201));
        mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", longWords, "levelCheck", brandNew)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAmbitiousDeadlineGetsAFirstMilestone() throws Exception {
        String email = uniqueEmail("plan-ambitious");
        String token = api.registerStudent(email);
        String deadline = LocalDate.now().plusDays(10).toString();
        Map<String, Object> goal = new HashMap<>(goal("TRAVEL",
                List.of("ORDER_FOOD", "ASK_DIRECTIONS", "GO_SHOPPING"), "LIGHT", List.of()));
        goal.put("deadline", deadline);
        JsonNode plan = build(token, goal, Map.of("selfLevel", "NEW_TO_MANDARIN", "skipped", true));

        assertThat(plan.path("milestone").path("date").asText()).isEqualTo(deadline);
        assertThat(plan.path("milestone").path("stepPositions").size()).isPositive();
        Long studentId = studentProfile(email).getId();
        assertThat(planRepository.findAll().stream().filter(saved -> saved.getStudent().getId().equals(studentId)))
                .singleElement()
                .satisfies(saved -> assertThat(saved.getMilestoneWeek()).isEqualTo(2));
        assertThat(plan.path("weeks").path("total").asInt()).isGreaterThan(2);
    }

    @Test
    void anewGoalOrAChangedLevelMakesANewVersionAndKeepsTheOldOne() throws Exception {
        String email = uniqueEmail("plan-versions");
        String token = api.registerStudent(email);
        build(token, goal("TRAVEL", List.of("ORDER_FOOD"), "REGULAR", List.of()),
                Map.of("selfLevel", "NEW_TO_MANDARIN", "skipped", true));

        JsonNode updated = api.data(mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of(
                        "goal", goal("WORK", List.of("GREET_CUSTOMERS", "SMALL_TALK"), "SERIOUS", List.of("SPEAKING")))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updated.path("goal").path("reason").asText()).isEqualTo("WORK");
        assertThat(updated.path("level").path("placed").asText()).isEqualTo("NEW_TO_MANDARIN");
        Long studentId = studentProfile(email).getId();
        assertThat(planRepository.findAll().stream().filter(plan -> plan.getStudent().getId().equals(studentId)))
                .hasSize(2)
                .filteredOn(plan -> plan.isActive())
                .singleElement()
                .satisfies(plan -> assertThat(plan.getVersion()).isEqualTo(2));

        // Retake: every answer right from Beginner 1 places the student higher, so the plan is rebuilt.
        List<Map<String, Object>> answers = finishCheck(token, "NEW_TO_MANDARIN", true);
        JsonNode retake = api.data(mockMvc.perform(api.jsonPost(PLAN + "/level-check", token, Map.of(
                        "startLevel", "NEW_TO_MANDARIN", "answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(retake.path("changed").asBoolean()).isTrue();
        assertThat(retake.path("level").asText()).isNotEqualTo("NEW_TO_MANDARIN");
        mockMvc.perform(api.authGet(PLAN + "/overview", token))
                .andExpect(jsonPath("$.data.plan.level").value(retake.path("level").asText()));

        // The same answers again: the level stays, so does the plan version.
        JsonNode again = api.data(mockMvc.perform(api.jsonPost(PLAN + "/level-check", token, Map.of(
                        "startLevel", "NEW_TO_MANDARIN", "answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(again.path("changed").asBoolean()).isFalse();
        assertThat(planRepository.findAll().stream().filter(plan -> plan.getStudent().getId().equals(studentId)))
                .hasSize(3);
    }

    @Test
    void deletingTheAccountRemovesTheGoalInTheStudentsOwnWords() throws Exception {
        String email = uniqueEmail("plan-delete");
        String token = api.registerStudent(email);
        Map<String, Object> goal = new HashMap<>(goal("TRAVEL", List.of("ORDER_FOOD"), "LIGHT", List.of()));
        goal.put("goalText", "Visiting my aunt in Ipoh next spring.");
        build(token, goal, Map.of("selfLevel", "NEW_TO_MANDARIN", "skipped", true));
        Long studentId = studentProfile(email).getId();

        mockMvc.perform(api.jsonPost("/api/v1/users/me/delete", token, Map.of("confirmEmail", email)))
                .andExpect(status().isOk());

        assertThat(planRepository.findAll().stream().filter(plan -> plan.getStudent().getId().equals(studentId)))
                .singleElement()
                .satisfies(plan -> {
                    assertThat(plan.getGoalText()).isNull();
                    assertThat(plan.getReason().name()).isEqualTo("TRAVEL");
                });
    }

    @Test
    void retakingOrUpdatingNeedsAPlan() throws Exception {
        String token = api.registerStudent(uniqueEmail("plan-none"));
        mockMvc.perform(api.jsonPut(PLAN + "/goal", token, Map.of("goal", goal("TRAVEL", List.of(), "LIGHT", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Build your learning plan first."));
    }

    @Test
    void classesComeFromTheCatalogueAndTheTeacherSeesTheGoalReadOnly() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("plan-teacher"));
        String otherTeacherToken = api.approvedTeacher(uniqueEmail("plan-other-teacher"));
        Long joinedClass = api.publishedClass(teacherToken, "Food and Conversation Club").classId();
        String email = uniqueEmail("plan-classes");
        String token = api.registerStudent(email);
        api.join(token, joinedClass);
        JsonNode plan = build(token, goal("EVERYDAY", List.of("ORDER_FOOD", "SHORT_CHATS"), "REGULAR", List.of()),
                Map.of("selfLevel", "NEW_TO_MANDARIN", "skipped", true));

        JsonNode classStep = stream(plan.path("steps"))
                .filter(step -> step.path("kind").asText().equals("CLASS") && step.path("refId").asLong() == joinedClass)
                .findFirst()
                .orElseThrow();
        assertThat(classStep.path("status").asText()).isEqualTo("IN_MY_CLASSES");
        assertThat(classStep.path("joined").asBoolean()).isTrue();
        assertThat(classStep.path("goals").toString()).contains("SHORT_CHATS");

        Long studentId = studentProfile(email).getId();
        String goalPath = "/api/v1/teachers/students/" + studentId + "/learning-goal";
        mockMvc.perform(api.authGet(goalPath, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("EVERYDAY"))
                .andExpect(jsonPath("$.data.yourClasses[0].classId").value(joinedClass))
                .andExpect(jsonPath("$.data.yourClasses[0].position").value(classStep.path("position").asInt()))
                .andExpect(jsonPath("$.data.ownWords").doesNotExist());
        mockMvc.perform(api.authGet(goalPath, otherTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(goalPath, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(PLAN, teacherToken))
                .andExpect(status().isForbidden());

        // A student in the teacher's class without a plan: nothing to show.
        String classmateEmail = uniqueEmail("plan-classmate");
        api.join(api.registerStudent(classmateEmail), joinedClass);
        mockMvc.perform(api.authGet("/api/v1/teachers/students/" + studentProfile(classmateEmail).getId()
                        + "/learning-goal", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ===== Helpers =====

    private JsonNode build(String token, Map<String, Object> goal, Map<String, Object> levelCheck) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost(PLAN, token, Map.of("goal", goal, "levelCheck", levelCheck)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static Map<String, Object> goal(String reason, List<String> goals, String pace, List<String> focus) {
        Map<String, Object> goal = new HashMap<>();
        goal.put("reason", reason);
        goal.put("goals", goals);
        goal.put("pace", pace);
        goal.put("focus", focus);
        if (reason.equals("OTHER")) {
            goal.put("reasonText", " ");
        }
        return goal;
    }

    /** Answers the adaptive check to the end: every answer right, or every answer "I don't know yet". */
    private List<Map<String, Object>> finishCheck(String token, String startLevel, boolean right) throws Exception {
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode step = next(token, startLevel, answers);
        while (!step.path("done").asBoolean()) {
            long questionId = step.path("next").path("id").asLong();
            answers.add(answer(questionId, right ? rightOption(questionId) : null));
            step = next(token, startLevel, answers);
        }
        return answers;
    }

    private JsonNode next(String token, String startLevel, List<Map<String, Object>> answers) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost(NEXT, token, Map.of("startLevel", startLevel, "answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private Long rightOption(long questionId) {
        return optionRepository.findByQuestionIdOrderByPositionAsc(questionId).stream()
                .filter(LevelCheckOption::isCorrect)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static Map<String, Object> answer(long questionId, Long optionId) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("questionId", questionId);
        answer.put("optionId", optionId);
        return answer;
    }

    private static List<Integer> learnUnitNumbers(JsonNode plan) {
        return stream(plan.path("steps"))
                .filter(step -> step.path("kind").asText().equals("LEARN_UNIT"))
                .map(step -> step.path("unitNumber").asInt())
                .toList();
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
