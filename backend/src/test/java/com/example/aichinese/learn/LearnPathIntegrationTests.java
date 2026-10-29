package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.support.ApiIntegrationTest;

/** The Learn path: sequential unlock, server-checked answers, first-completion XP, checkpoint and unit badges. */
class LearnPathIntegrationTests extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/students/learn/path";

    @Autowired
    private LearnLessonRepository lessonRepository;

    @Autowired
    private LearnQuestionRepository questionRepository;

    @Autowired
    private LearnUnitRepository unitRepository;

    @Test
    void aNewStudentStartsAtTheFirstLessonAndDraftUnitsAreHidden() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-new"));
        JsonNode path = api.data(mockMvc.perform(api.authGet(PATH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedLessons").value(0))
                .andExpect(jsonPath("$.data.nextLesson.title").value("Hello and goodbye"))
                .andExpect(jsonPath("$.data.nextLesson.position").value(1))
                .andExpect(jsonPath("$.data.nextLesson.unitLessonCount").value(7))
                .andExpect(jsonPath("$.data.levels[0].level").value(1))
                .andExpect(jsonPath("$.data.levels[0].locked").value(false))
                .andExpect(jsonPath("$.data.levels[0].units[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[0].state").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[0].current").value(true))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[1].state").value("LOCKED"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[1].unlockAfter").value("Hello and goodbye"))
                .andExpect(jsonPath("$.data.levels[0].units[1].state").value("LOCKED"))
                .andExpect(jsonPath("$.data.levels[0].units[1].unlockAfterUnit").value(1))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.state").value("LOCKED"))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.questionCount").value(10))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.xpReward").value(50))
                .andExpect(jsonPath("$.data.levels[1].locked").value(true))
                .andReturn());

        List<String> unitTitles = path.findValuesAsText("title");
        assertThat(unitTitles).contains("Say hello", "Campus life", "Shopping", "Travel").doesNotContain("Work and study");
        int publishedLessons = (int) lessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getUnit().getPublishStatus() == PublishStatus.PUBLISHED)
                .filter(lesson -> lesson.getLessonKind() != LearnLessonKind.CHECKPOINT)
                .count();
        assertThat(path.path("totalLessons").asInt()).isEqualTo(publishedLessons);
    }

    @Test
    void lessonsSendNoAnswersAndLockedLessonsCannotBeOpened() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-lock"));
        LearnLesson first = lesson("Hello and goodbye");
        LearnLesson second = lesson("Polite phrases");

        JsonNode lesson = api.data(mockMvc.perform(api.authGet("/api/v1/students/learn/lessons/" + first.getId(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Hello and goodbye"))
                .andExpect(jsonPath("$.data.unitNumber").value(1))
                .andExpect(jsonPath("$.data.xpReward").value(10))
                .andReturn());
        assertThat(lesson.path("questions").size()).isBetween(5, 8);
        assertThat(lesson.toString()).doesNotContain("\"answer\"").doesNotContain("explanation");
        // A meaning question shows the word but not its meaning; a listening question does not show the word.
        for (JsonNode question : lesson.path("questions")) {
            if ("MEANING".equals(question.path("type").asText())) {
                assertThat(question.path("meaning").isNull()).isTrue();
            }
            if (question.path("type").asText().startsWith("LISTEN")) {
                assertThat(question.path("hanzi").isNull()).isTrue();
                assertThat(question.path("audioText").asText()).isNotBlank();
            }
        }

        mockMvc.perform(api.authGet("/api/v1/students/learn/lessons/" + second.getId(), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This lesson is still locked. Finish the lessons before it first."));
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + second.getId() + "/complete", token,
                        Map.of("answers", answers(second))))
                .andExpect(status().isConflict());
    }

    @Test
    void checkingAnswersRecordsMistakesAndFinishingUnlocksTheNextLessonOnce() throws Exception {
        String email = uniqueEmail("path-flow");
        String token = api.registerStudent(email);
        LearnLesson first = lesson("Hello and goodbye");
        LearnQuestion question = questionRepository.findByLessonIdOrderByPositionAsc(first.getId()).get(0);
        String lessonPath = "/api/v1/students/learn/lessons/" + first.getId();

        mockMvc.perform(api.jsonPost(lessonPath + "/check", token,
                        Map.of("questionId", question.getId(), "answer", "something else")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value(question.getAnswer()))
                .andExpect(jsonPath("$.data.explanation").isNotEmpty());
        mockMvc.perform(api.jsonPost(lessonPath + "/check", token,
                        Map.of("questionId", question.getId(), "answer", question.getAnswer())))
                .andExpect(jsonPath("$.data.correct").value(true));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.learn").value(1))
                .andExpect(jsonPath("$.data.page.items[0].yourAnswer").value("something else"));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(jsonPath("$.data.context[0].sourceTitle").value("Hello and goodbye"));

        mockMvc.perform(api.jsonPost(lessonPath + "/complete", token,
                        Map.of("answers", answers(first), "durationSeconds", 222)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.accuracy").value(100))
                .andExpect(jsonPath("$.data.durationSeconds").value(222))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10))
                .andExpect(jsonPath("$.data.xp.todayXp").value(10))
                .andExpect(jsonPath("$.data.xp.dailyGoalXp").value(20))
                .andExpect(jsonPath("$.data.xp.goalJustReached").value(false))
                .andExpect(jsonPath("$.data.xp.newBadges[0]").value("FIRST_STEPS"))
                .andExpect(jsonPath("$.data.nextLesson.title").value("Polite phrases"))
                .andExpect(jsonPath("$.data.nextLesson.unlockedNow").value(true));

        // A replay is welcome but earns nothing twice; a wrong replay still finishes a lesson.
        mockMvc.perform(api.jsonPost(lessonPath + "/complete", token, Map.of("answers", List.of())))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.accuracy").value(0))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));

        // The second lesson reaches the default goal of 20 XP: +5 bonus and a streak of one day.
        LearnLesson second = lesson("Polite phrases");
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + second.getId() + "/complete", token,
                        Map.of("answers", answers(second))))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10))
                .andExpect(jsonPath("$.data.xp.bonusXp").value(5))
                .andExpect(jsonPath("$.data.xp.todayXp").value(25))
                .andExpect(jsonPath("$.data.xp.goalJustReached").value(true))
                .andExpect(jsonPath("$.data.xp.currentStreak").value(1));
        assertThat(studentProfile(email).getTotalPoints()).isEqualTo(25);

        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.completedLessons").value(2))
                .andExpect(jsonPath("$.data.nextLesson.title").value("Introduce yourself"))
                .andExpect(jsonPath("$.data.levels[0].units[0].completedCount").value(2));
        // Another student's path is untouched.
        String other = api.registerStudent(uniqueEmail("path-other"));
        mockMvc.perform(api.authGet(PATH, other))
                .andExpect(jsonPath("$.data.completedLessons").value(0));
    }

    @Test
    void bonusChestsAreOptionalAndFinishingAUnitEarnsUnitChampion() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-unit"));
        List<LearnLesson> unitOne = lessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getUnit().getPosition() == 1)
                .toList();
        List<String> badges = new ArrayList<>();
        for (LearnLesson lesson : unitOne) {
            if (lesson.getLessonKind() == LearnLessonKind.BONUS) {
                continue;
            }
            JsonNode result = complete(token, lesson);
            result.path("xp").path("newBadges").forEach(badge -> badges.add(badge.asText()));
        }
        assertThat(badges).contains("FIRST_STEPS", "LESSON_FINISHER", "UNIT_CHAMPION", "XP_STARTER");
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[0].units[0].state").value("DONE"))
                .andExpect(jsonPath("$.data.levels[0].units[1].state").value("ACTIVE"))
                .andExpect(jsonPath("$.data.nextLesson.title").value("At the canteen"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[4].kind").value("BONUS"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[4].state").value("AVAILABLE"));
    }

    @Test
    void theCheckpointMustBePassedToOpenLevelTwo() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-checkpoint"));
        List<LearnLesson> levelOne = lessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getUnit().getPathLevel() == 1)
                .filter(lesson -> lesson.getLessonKind() == LearnLessonKind.LESSON
                        || lesson.getLessonKind() == LearnLessonKind.REVIEW)
                .toList();
        for (LearnLesson lesson : levelOne) {
            complete(token, lesson);
        }
        LearnLesson checkpoint = lessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getLessonKind() == LearnLessonKind.CHECKPOINT)
                .findFirst()
                .orElseThrow();
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.levels[1].locked").value(true));

        String checkpointPath = "/api/v1/students/learn/lessons/" + checkpoint.getId();
        mockMvc.perform(api.authGet(checkpointPath, token))
                .andExpect(jsonPath("$.data.passPercent").value(70))
                .andExpect(jsonPath("$.data.questions.length()").value(10));
        mockMvc.perform(api.jsonPost(checkpointPath + "/complete", token, Map.of("answers", List.of())))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0))
                .andExpect(jsonPath("$.data.nextLesson").doesNotExist());
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[1].locked").value(true));

        mockMvc.perform(api.jsonPost(checkpointPath + "/complete", token, Map.of("answers", answers(checkpoint))))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(50))
                .andExpect(jsonPath("$.data.nextLesson.title").value("Clothes and colours"));
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.state").value("DONE"))
                .andExpect(jsonPath("$.data.levels[1].locked").value(false));
    }

    @Test
    void theGuidebookListsKeyPhrasesAndTips() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-guide"));
        LearnUnit unit = unitRepository.findAllByOrderByPositionAsc().get(0);
        mockMvc.perform(api.authGet("/api/v1/students/learn/units/" + unit.getId() + "/guidebook", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Say hello"))
                .andExpect(jsonPath("$.data.phrases[0].hanzi").value("你好"))
                .andExpect(jsonPath("$.data.phrases.length()").value(5))
                .andExpect(jsonPath("$.data.tips.length()").value(3));
    }

    @Test
    void onlyStudentsUseTheLearnPath() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("path-teacher"));
        mockMvc.perform(api.authGet(PATH, teacherToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(PATH, api.adminToken())).andExpect(status().isForbidden());
    }

    private JsonNode complete(String token, LearnLesson lesson) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + lesson.getId() + "/complete",
                        token, Map.of("answers", answers(lesson))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private LearnLesson lesson(String title) {
        return lessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }

    /** The right answer to every question of the lesson (read from the server's own answer key). */
    private List<Map<String, Object>> answers(LearnLesson lesson) {
        return questionRepository.findByLessonIdOrderByPositionAsc(lesson.getId()).stream()
                .map(question -> Map.<String, Object>of("questionId", question.getId(), "answer", question.getAnswer()))
                .toList();
    }
}
