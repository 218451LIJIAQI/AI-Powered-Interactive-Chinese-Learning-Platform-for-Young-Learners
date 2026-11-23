package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * The Learn path: sequential unlock, server-checked answers, first-completion XP, checkpoint and unit badges, hints
 * held to the level's limit.
 */
class LearnPathIntegrationTests extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/students/learn/path";
    private static final String FIRST = "The four tones · 声调";
    private static final String SECOND = "Simple finals · a o e i u ü";

    @Autowired
    private LearnUnitRepository unitRepository;

    @Autowired
    private LearnLessonCompletionRepository completionRepository;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Test
    void aNewStudentStartsAtTheFirstLessonAndDraftUnitsAreHidden() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-new"));
        JsonNode path = api.data(mockMvc.perform(api.authGet(PATH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedLessons").value(0))
                .andExpect(jsonPath("$.data.nextLesson.title").value(FIRST))
                // Unit 1 has 7 lessons (its review included) and an optional bonus chest, which is no lesson.
                .andExpect(jsonPath("$.data.levels[0].units[0].lessonCount").value(7))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons.length()").value(8))
                .andExpect(jsonPath("$.data.levels[0].level").value(1))
                .andExpect(jsonPath("$.data.levels[0].locked").value(false))
                .andExpect(jsonPath("$.data.levels[0].units[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[0].state").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[0].current").value(true))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[1].state").value("LOCKED"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[1].unlockAfter").value(FIRST))
                .andExpect(jsonPath("$.data.levels[0].units[1].state").value("LOCKED"))
                .andExpect(jsonPath("$.data.levels[0].units[1].unlockAfterUnit").value(1))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.state").value("LOCKED"))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.questionCount").value(10))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.xpReward").value(50))
                .andExpect(jsonPath("$.data.levels[1].locked").value(true))
                .andReturn());

        List<String> unitTitles = path.findValuesAsText("title");
        assertThat(unitTitles).contains("Pinyin and tones", "Questions and counting", "Time and completed actions", "Comparing")
                .doesNotContain("Actions and experience", "Abilities and reasons");
        int publishedLessons = (int) learnLessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getUnit().getPublishStatus() == PublishStatus.PUBLISHED)
                .filter(lesson -> lesson.getLessonKind() == LearnLessonKind.LESSON
                        || lesson.getLessonKind() == LearnLessonKind.REVIEW)
                .count();
        int pathLessons = 0;
        for (JsonNode level : path.path("levels")) {
            for (JsonNode unit : level.path("units")) {
                pathLessons += unit.path("lessonCount").asInt();
            }
        }
        assertThat(pathLessons).isEqualTo(publishedLessons);
    }

    @Test
    void lessonsSendNoAnswersAndLockedLessonsCannotBeOpened() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-lock"));
        LearnLesson first = learnLesson(FIRST);
        LearnLesson second = learnLesson(SECOND);

        JsonNode lesson = api.data(mockMvc.perform(api.authGet("/api/v1/students/learn/lessons/" + first.getId(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(FIRST))
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
                        Map.of("answers", rightAnswers(second))))
                .andExpect(status().isConflict());
    }

    @Test
    void checkingAnswersRecordsMistakesAndFinishingUnlocksTheNextLessonOnce() throws Exception {
        String email = uniqueEmail("path-flow");
        String token = api.registerStudent(email);
        LearnLesson first = learnLesson(FIRST);
        LearnQuestion question = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(first.getId()).get(0);
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
                .andExpect(jsonPath("$.data.context[0].sourceTitle").value(FIRST));

        List<Map<String, Object>> answers = rightAnswers(first);
        mockMvc.perform(api.jsonPost(lessonPath + "/complete", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.accuracy").value(100))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10))
                .andExpect(jsonPath("$.data.xp.todayXp").value(10))
                .andExpect(jsonPath("$.data.xp.dailyGoalXp").value(20))
                .andExpect(jsonPath("$.data.xp.goalReached").value(false))
                .andExpect(jsonPath("$.data.xp.newBadges[0]").value("FIRST_STEPS"))
                .andExpect(jsonPath("$.data.nextLesson.title").value(SECOND))
                .andExpect(jsonPath("$.data.nextLesson.unlockedNow").value(true));

        // A lesson is finished by going through it: a completion without the questions is refused.
        mockMvc.perform(api.jsonPost(lessonPath + "/complete", token, Map.of("answers", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Answer or skip every question before you finish the lesson."));
        // A replay is welcome but earns nothing twice; a replay that skips everything still finishes a lesson.
        mockMvc.perform(api.jsonPost(lessonPath + "/complete", token, Map.of("answers", skipAll(answers))))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.accuracy").value(0))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));

        // The second lesson reaches the default goal of 20 XP: +5 bonus and a streak of one day.
        LearnLesson second = learnLesson(SECOND);
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + second.getId() + "/complete", token,
                        Map.of("answers", rightAnswers(second))))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10))
                .andExpect(jsonPath("$.data.xp.bonusXp").value(5))
                .andExpect(jsonPath("$.data.xp.todayXp").value(25))
                .andExpect(jsonPath("$.data.xp.goalReached").value(true))
                .andExpect(jsonPath("$.data.xp.currentStreak").value(1));
        assertThat(studentProfile(email).getTotalPoints()).isEqualTo(25);

        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.completedLessons").value(2))
                .andExpect(jsonPath("$.data.nextLesson.title").value("Initials 1 · b p m f and more"))
                .andExpect(jsonPath("$.data.levels[0].units[0].completedCount").value(2));
        // Another student's path is untouched.
        String other = api.registerStudent(uniqueEmail("path-other"));
        mockMvc.perform(api.authGet(PATH, other))
                .andExpect(jsonPath("$.data.completedLessons").value(0));
    }

    @Test
    void skippedFirstAnswersStaySkippedWhenTheSubmissionRepeatsQuestions() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-first-answer"));
        LearnLesson first = learnLesson(FIRST);
        List<Map<String, Object>> submitted = skipThenAnswer(rightAnswers(first));
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + first.getId() + "/complete",
                        token, Map.of("answers", submitted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accuracy").value(0))
                // Ordinary lessons may still finish at zero accuracy; their existing XP rule is unchanged.
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10));
    }

    @Test
    void bonusChestsAreOptionalAndFinishingAUnitEarnsUnitChampion() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-unit"));
        List<LearnLesson> unitOne = unitOneLessons();
        List<String> badges = new ArrayList<>();
        for (LearnLesson lesson : unitOne) {
            if (lesson.getLessonKind() == LearnLessonKind.BONUS) {
                continue;
            }
            JsonNode result = complete(token, lesson);
            result.path("xp").path("newBadges").forEach(badge -> badges.add(badge.asText()));
        }
        assertThat(badges).contains("FIRST_STEPS", "LESSON_FINISHER", "UNIT_CHAMPION", "XP_STARTER");
        // Unit 1 is complete at seven lessons even with its bonus chest unopened. The next lesson starts Unit 2.
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[0].units[0].state").value("DONE"))
                .andExpect(jsonPath("$.data.levels[0].units[0].completedCount").value(7))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessonCount").value(7))
                .andExpect(jsonPath("$.data.completedLessons").value(7))
                .andExpect(jsonPath("$.data.levels[0].units[1].state").value("ACTIVE"))
                .andExpect(jsonPath("$.data.nextLesson.title").value("Strokes and order · 笔画"))
                .andExpect(jsonPath("$.data.levels[0].units[1].lessonCount").value(5))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[3].position").value(4))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[4].kind").value("BONUS"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[4].state").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[5].title").value("Compound finals · ai ei ao ou"))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[5].position").value(5))
                .andExpect(jsonPath("$.data.levels[0].units[0].lessons[7].position").value(7));
        // Home counts the same lessons as the path: Unit 2 has 5, numbered 1 to 5, its bonus chest aside.
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.next.unitNumber").value(2))
                .andExpect(jsonPath("$.data.next.stops.length()").value(5))
                .andExpect(jsonPath("$.data.next.stops[4].position").value(5));
    }

    @Test
    void aBonusChestIsNoLessonForLessonFinisher() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-finisher"));
        List<LearnLesson> unitOne = unitOneLessons();
        // Four lessons and the bonus chest after them: five stops, but four lessons.
        for (LearnLesson lesson : unitOne.subList(0, 5)) {
            assertThat(complete(token, lesson).path("xp").path("newBadges").toString()).doesNotContain("LESSON_FINISHER");
        }
        assertThat(unitOne.get(4).getLessonKind()).isEqualTo(LearnLessonKind.BONUS);
        JsonNode fifth = complete(token, unitOne.get(5));
        assertThat(fifth.path("xp").path("newBadges").toString()).contains("LESSON_FINISHER");
    }

    /**
     * The checkpoint is the gate of Level 1, not part of its last unit: Unit 4 is done, with Unit Champion, once its
     * lessons are, and the checkpoint is then the path's next step until it is passed and opens Level 2.
     */
    @Test
    void theCheckpointMustBePassedToOpenLevelTwo() throws Exception {
        String email = uniqueEmail("path-checkpoint");
        String token = api.registerStudent(email);
        List<LearnLesson> path = learnLessonRepository.findPathInOrder();
        List<LearnLesson> levelOne = path.stream()
                .filter(lesson -> lesson.getUnit().getPathLevel() == 1)
                .filter(lesson -> lesson.getLessonKind() == LearnLessonKind.LESSON
                        || lesson.getLessonKind() == LearnLessonKind.REVIEW)
                .toList();
        // Units 1 to 3 written as done (so no badge yet), then Unit 4 played: its last lesson finishes the unit.
        StudentProfile student = studentProfile(email);
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        completionRepository.saveAll(levelOne.stream().filter(lesson -> lesson.getUnit().getPosition() < 4)
                .map(lesson -> completion(student, lesson, yesterday)).toList());
        List<LearnLesson> unitFour = levelOne.stream().filter(lesson -> lesson.getUnit().getPosition() == 4).toList();
        for (LearnLesson lesson : unitFour.subList(0, unitFour.size() - 1)) {
            assertThat(complete(token, lesson).path("xp").path("newBadges").toString()).doesNotContain("UNIT_CHAMPION");
        }
        JsonNode review = complete(token, unitFour.getLast());
        assertThat(review.path("kind").asText()).isEqualTo("REVIEW");
        assertThat(review.path("xp").path("newBadges").toString()).contains("UNIT_CHAMPION");
        LearnLesson checkpoint = path.stream()
                .filter(lesson -> lesson.getLessonKind() == LearnLessonKind.CHECKPOINT)
                .findFirst()
                .orElseThrow();
        assertThat(review.path("nextLesson").path("title").asText()).isEqualTo(checkpoint.getTitle());
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[0].units[3].state").value("DONE"))
                .andExpect(jsonPath("$.data.levels[0].units[3].completedCount").value(unitFour.size()))
                .andExpect(jsonPath("$.data.levels[0].units[3].lessonCount").value(unitFour.size()))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.completedLessons").value(25))
                // The checkpoint is no lesson: the path's next step is the level's checkpoint, not a next lesson.
                .andExpect(jsonPath("$.data.nextLesson").doesNotExist())
                .andExpect(jsonPath("$.data.levels[1].locked").value(true))
                // Unit 5 waits for the checkpoint, not for Unit 4, which is done.
                .andExpect(jsonPath("$.data.levels[1].units[0].state").value("LOCKED"))
                .andExpect(jsonPath("$.data.levels[1].units[0].unlockAfterUnit").doesNotExist());

        String checkpointPath = "/api/v1/students/learn/lessons/" + checkpoint.getId();
        List<Long> questionIds = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(checkpoint.getId()).stream()
                .map(LearnQuestion::getId).toList();
        assertThat(questionIds).hasSize(10);
        int pointsBefore = studentProfile(email).getTotalPoints();
        String moveQuestions = "update learn_questions set lesson_id = :lesson where id in (:questions)";
        namedJdbc.update(moveQuestions, Map.of("lesson", unitFour.getLast().getId(), "questions", questionIds));
        try {
            mockMvc.perform(api.authGet(checkpointPath, token))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("This lesson has no questions yet."));
            mockMvc.perform(api.jsonPost(checkpointPath + "/complete", token, Map.of("answers", List.of())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("This lesson has no questions yet."));
            assertThat(completionRepository.findCompletedLessonIds(student.getId())).doesNotContain(checkpoint.getId());
            assertThat(studentProfile(email).getTotalPoints()).isEqualTo(pointsBefore);
        } finally {
            namedJdbc.update(moveQuestions, Map.of("lesson", checkpoint.getId(), "questions", questionIds));
        }
        JsonNode run = api.data(mockMvc.perform(api.authGet(checkpointPath, token))
                .andExpect(jsonPath("$.data.passPercent").value(70))
                .andExpect(jsonPath("$.data.questions.length()").value(10))
                .andReturn());
        LearnQuestion firstQuestion = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(checkpoint.getId()).get(0);
        mockMvc.perform(api.jsonPost(checkpointPath + "/hint", token, hintRequest(firstQuestion, run)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Checkpoints have no hints."));
        List<Map<String, Object>> checkpointAnswers = rightAnswers(checkpoint);
        mockMvc.perform(api.jsonPost(checkpointPath + "/complete", token, Map.of("answers", skipAll(checkpointAnswers))))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0))
                .andExpect(jsonPath("$.data.nextLesson").doesNotExist());
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[1].locked").value(true));

        List<Map<String, Object>> repeated = skipThenAnswer(checkpointAnswers);
        mockMvc.perform(api.jsonPost(checkpointPath + "/complete", token, Map.of("answers", repeated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accuracy").value(0))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[1].locked").value(true));

        mockMvc.perform(api.jsonPost(checkpointPath + "/complete", token, Map.of("answers", checkpointAnswers)))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(50))
                .andExpect(jsonPath("$.data.xp.newBadges").isEmpty())
                .andExpect(jsonPath("$.data.nextLesson.title").value("Yesterday and today · 昨天"));
        mockMvc.perform(api.authGet(PATH, token))
                .andExpect(jsonPath("$.data.levels[0].checkpoint.state").value("DONE"))
                .andExpect(jsonPath("$.data.levels[1].locked").value(false));
    }

    /**
     * Each signed lesson run has its own allowance. Repeating a hint is free, and a request with a changed start,
     * a missing signature or another learner's signature is rejected. Hints preserve the usual grading and XP.
     */
    @Test
    void aLessonGetsHintsForAsManyQuestionsAsItsLevelAllowsAndReadingItAgainStartsANewRun() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-hints"));
        LearnLesson first = learnLesson(FIRST);
        List<LearnQuestion> questions = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(first.getId());
        String lessonPath = "/api/v1/students/learn/lessons/" + first.getId();
        try (HintRules rules = HintRules.saved(mockMvc, api)) {
            rules.setHsk1(2);
            JsonNode run = api.data(mockMvc.perform(api.authGet(lessonPath, token)).andExpect(status().isOk()).andReturn());
            assertThat(run.path("runStartedAt").asText()).isNotBlank();
            assertThat(run.path("runSignature").asText()).isNotBlank();

            // Two questions with a hint use the run's two hints.
            for (LearnQuestion question : questions.subList(0, 2)) {
                mockMvc.perform(api.jsonPost(lessonPath + "/hint", token, hintRequest(question, run)))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(api.jsonPost(lessonPath + "/hint", token, hintRequest(questions.get(2), run)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There are no hints left in this set."));
            // A question that had its hint in this run may ask again and gets the same hint.
            mockMvc.perform(api.jsonPost(lessonPath + "/hint", token, hintRequest(questions.get(1), run)))
                    .andExpect(status().isOk());
            // A run starting now, which no hint of this run would count, is not one the server started.
            Map<String, Object> moved = new HashMap<>(hintRequest(questions.get(2), run));
            moved.put("runStartedAt", OffsetDateTime.now().toString());
            mockMvc.perform(api.jsonPost(lessonPath + "/hint", token, moved))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));
            // A hint request names its run, with the server's signature.
            mockMvc.perform(api.jsonPost(lessonPath + "/hint", token,
                            Map.of("questionId", questions.get(2).getId(),
                                    "runStartedAt", run.path("runStartedAt").asText())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));
            mockMvc.perform(api.jsonPost(lessonPath + "/hint", token,
                            Map.of("questionId", questions.get(2).getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));
            // Another student's run of the same lesson is not this student's.
            String other = api.registerStudent(uniqueEmail("path-hints-other"));
            mockMvc.perform(api.jsonPost(lessonPath + "/hint", other, hintRequest(questions.get(2), run)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));

            // The lesson read again is a new run with its own hints.
            JsonNode next = api.data(mockMvc.perform(api.authGet(lessonPath, token)).andReturn());
            mockMvc.perform(api.jsonPost(lessonPath + "/hint", token, hintRequest(questions.get(2), next)))
                    .andExpect(status().isOk());
            // Right answers after hints are graded right and earn the lesson's XP as without them.
            mockMvc.perform(api.jsonPost(lessonPath + "/check", token,
                            Map.of("questionId", questions.get(0).getId(), "answer", questions.get(0).getAnswer())))
                    .andExpect(jsonPath("$.data.correct").value(true));
            mockMvc.perform(api.jsonPost(lessonPath + "/complete", token, Map.of("answers", rightAnswers(first))))
                    .andExpect(jsonPath("$.data.accuracy").value(100))
                    .andExpect(jsonPath("$.data.xp.xpEarned").value(10));
        }
    }

    @Test
    void theGuidebookListsKeyPhrasesAndTips() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-guide"));
        LearnUnit unit = unitRepository.findAllByOrderByPositionAsc().get(0);
        mockMvc.perform(api.authGet("/api/v1/students/learn/units/" + unit.getId() + "/guidebook", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phrases[0].hanzi").value("妈"))
                .andExpect(jsonPath("$.data.phrases.length()").value(5))
                .andExpect(jsonPath("$.data.tips.length()").value(4));
    }

    @Test
    void aPronunciationLessonAsksOnlyForThePinyinAndWhatYouHear() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-sounds"));
        LearnLesson initials = learnLesson("Initials 1 · b p m f and more");
        for (LearnLesson done : List.of(learnLesson(FIRST), learnLesson(SECOND))) {
            complete(token, done);
        }
        JsonNode lesson = api.data(mockMvc.perform(api.authGet("/api/v1/students/learn/lessons/" + initials.getId(), token))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode sample = lesson.path("questions").get(0);
        assertThat(sample.path("type").asText()).isEqualTo("PINYIN");
        assertThat(sample.path("hanzi").asText()).isEqualTo("爸");
        assertThat(sample.path("options").findValuesAsText("text")).containsExactly("bà", "pà", "mà");
        assertThat(lesson.path("questions").findValuesAsText("type"))
                .containsOnly("PINYIN", "LISTEN_CHOOSE", "LISTEN_BUILD");

        JsonNode drill = lesson.path("questions").get(1);
        assertThat(drill.path("type").asText()).isEqualTo("LISTEN_BUILD");
        assertThat(drill.path("tiles").toString()).contains("mā", "pà", "dú", "hē", "bà", "tū", "gē");
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + initials.getId() + "/check", token,
                        Map.of("questionId", drill.path("id").asLong(), "answer", "mā bà dú hē")))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value("mā pà dú hē"))
                .andExpect(jsonPath("$.data.explanation").value(containsString("puff of air")));
    }

    @Test
    void onlyStudentsUseTheLearnPath() throws Exception {
        mockMvc.perform(api.authGet(PATH, api.adminToken())).andExpect(status().isForbidden());
    }

    private List<LearnLesson> unitOneLessons() {
        return learnLessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getUnit().getPosition() == 1)
                .toList();
    }

    /** A hint request for the run of a lesson as the server sent it. */
    private static Map<String, Object> hintRequest(LearnQuestion question, JsonNode lesson) {
        return Map.of("questionId", question.getId(),
                "runStartedAt", lesson.path("runStartedAt").asText(),
                "runSignature", lesson.path("runSignature").asText());
    }

    private JsonNode complete(String token, LearnLesson lesson) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + lesson.getId() + "/complete",
                        token, Map.of("answers", rightAnswers(lesson))))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Every question of the lesson skipped, as the player sends a "Skip" (an entry without an answer). */
    private static List<Map<String, Object>> skipAll(List<Map<String, Object>> answers) {
        return answers.stream()
                .map(answer -> Map.of("questionId", answer.get("questionId")))
                .toList();
    }

    private static List<Map<String, Object>> skipThenAnswer(List<Map<String, Object>> answers) {
        List<Map<String, Object>> submitted = new ArrayList<>();
        for (Map<String, Object> answer : answers) {
            submitted.add(Map.of("questionId", answer.get("questionId")));
            submitted.add(answer);
        }
        return submitted;
    }
}
