package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.dto.classes.ClassRequests.ChosenPair;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A learner's way through a class the admins made: the catalogue, joining (lessons stay closed until then) and
 * leaving, lesson activities checked on the server, the lesson quiz with saved answers, hints, leaving it midway and the
 * result, the progress that follows (completed, class percent), and an archived class kept read-only. Classes never
 * give XP.
 */
class StudentClassJourneyIntegrationTests extends ApiIntegrationTest {

    private static final String STUDENT = "/api/v1/students/classes";
    private static final String ARCHIVED_READ_ONLY = "This class is archived. You can still open its lessons and your results.";

    @Autowired
    private QuizAttemptRepository attemptRepository;

    @Autowired
    private QuizAttemptAnswerRepository answerRepository;

    @Test
    void catalogueShowsPublishedClassesAndLessonsOpenOnlyAfterJoining() throws Exception {
        String adminToken = api.adminToken();
        String title = "Journey class " + uniqueSuffix();
        PublishedClass published = api.publishedClass(adminToken, title, "First words", "Hello and thanks");
        String draftTitle = "Journey hidden draft " + uniqueSuffix();
        api.draftClass(adminToken, draftTitle);
        String studentToken = api.registerStudent(uniqueEmail("journey-student"));

        // The catalogue lists the newest classes first; no card names a teacher.
        mockMvc.perform(get("/api/v1/public/classes?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title == '" + title + "')]").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.title == '" + title + "')].teacherName").isEmpty())
                .andExpect(jsonPath("$.data.items[?(@.title == '" + draftTitle + "')]").isEmpty());
        mockMvc.perform(get("/api/v1/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").isString());
        mockMvc.perform(api.authGet(STUDENT + "/catalogue?size=100", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + published.classId() + ")]").isNotEmpty());

        // Before joining: a preview, no lessons.
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined").value(false))
                .andExpect(jsonPath("$.data.archived").value(false))
                .andExpect(jsonPath("$.data.preview[0].lessons[0].title").value("First words"));
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Join this class to open its lessons."));
        mockMvc.perform(api.authPost(STUDENT + "/quizzes/" + published.quizId() + "/attempts", studentToken))
                .andExpect(status().isConflict());

        api.join(studentToken, published.classId());
        api.join(studentToken, published.classId());
        mockMvc.perform(api.authGet(STUDENT, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classes.length()").value(1))
                .andExpect(jsonPath("$.data.classes[0].summary.id").value(published.classId()))
                .andExpect(jsonPath("$.data.classes[0].progressPercent").value(0))
                .andExpect(jsonPath("$.data.classes[0].archived").value(false))
                .andExpect(jsonPath("$.data.classes[0].nextStep.lessonId").value(published.lessonId()))
                .andExpect(jsonPath("$.data.classes[0].nextStep.stepsDone").value(0))
                .andExpect(jsonPath("$.data.classes[0].nextStep.stepsTotal").value(4))
                .andExpect(jsonPath("$.data.liveToday").doesNotExist());
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), studentToken))
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.summary.lessonCount").value(4))
                .andExpect(jsonPath("$.data.units[0].lessons[0].status").value("NOT_STARTED"));
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherName").doesNotExist())
                .andExpect(jsonPath("$.data.archived").value(false))
                .andExpect(jsonPath("$.data.quizzes[0].questionCount").value(5));
    }

    @Test
    void aSharedUnitOpensInTheLearnersJoinedClassAndCannotBeEditedAcrossClasses() throws Exception {
        String admin = api.adminToken();
        PublishedClass original = api.publishedClass(admin, "Original class " + uniqueSuffix());
        String secondTitle = "Second class " + uniqueSuffix();
        PublishedClass second = api.publishedClass(admin, secondTitle);
        String student = api.registerStudent(uniqueEmail("legacy-shared-unit"));
        api.join(student, second.classId());

        // Legacy classes could refer to the same unit. Keep both classes' normal two-unit structure.
        String replaceUnit = "update package_items set item_id = ? where course_package_id = ? and item_id = ?";
        assertThat(jdbc.update(replaceUnit, original.unitId(), second.classId(), second.unitId())).isOne();
        try {
            String lesson = STUDENT + "/lessons/" + original.lessonId();
            mockMvc.perform(api.authGet(lesson, student))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.classId").value(second.classId()))
                    .andExpect(jsonPath("$.data.classTitle").value(secondTitle));
            mockMvc.perform(api.jsonPut("/api/v1/admin/lessons/" + original.lessonId() + "/content", admin,
                            Map.of("title", "Changed in both classes")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "This unit belongs to multiple classes. Resolve its ownership before editing it."));

            api.join(student, original.classId());
            mockMvc.perform(api.authPost("/api/v1/admin/classes/" + original.classId() + "/archive", admin))
                    .andExpect(status().isOk());
            mockMvc.perform(api.authGet(lesson, student))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.classId").value(second.classId()))
                    .andExpect(jsonPath("$.data.classTitle").value(secondTitle))
                    .andExpect(jsonPath("$.data.archived").value(false));
        } finally {
            jdbc.update(replaceUnit, second.unitId(), second.classId(), original.unitId());
        }
    }

    @Test
    void cataloguePagesAndCountsExcludeJoinedClassesOnlyForTheLearner() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass earlier = api.publishedClass(adminToken, "Earlier catalogue class " + uniqueSuffix());
        PublishedClass latest = api.publishedClass(adminToken, "Latest catalogue class " + uniqueSuffix());
        api.publishedLesson(adminToken, earlier.unitId(), "One more lesson", "An extra published lesson", true, null);
        api.draftClass(adminToken, "Draft catalogue class " + uniqueSuffix());
        String studentToken = api.registerStudent(uniqueEmail("catalogue-pages"));
        api.join(studentToken, latest.classId());

        JsonNode firstPage = api.data(mockMvc.perform(get("/api/v1/public/classes?size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(latest.classId()))
                .andExpect(jsonPath("$.data.items[0].lessonCount").value(4))
                .andReturn());
        long publicTotal = firstPage.path("totalItems").asLong();
        mockMvc.perform(get("/api/v1/public/classes?page=1&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(earlier.classId()))
                .andExpect(jsonPath("$.data.items[0].lessonCount").value(5))
                .andExpect(jsonPath("$.data.totalItems").value(publicTotal));
        mockMvc.perform(api.authGet(STUDENT + "/catalogue?size=1", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(earlier.classId()))
                .andExpect(jsonPath("$.data.totalItems").value(publicTotal - 1));
        mockMvc.perform(api.authGet(STUDENT, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moreClassCount").value(publicTotal - 1));
        mockMvc.perform(api.authGet(STUDENT + "/catalogue/membership", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinedCount").value(1))
                .andExpect(jsonPath("$.data.plannedClassIds").isEmpty());
        mockMvc.perform(get("/api/v1/public/classes?page=" + Integer.MAX_VALUE + "&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.totalItems").value(publicTotal));
    }

    /**
     * Leaving is the Undo of joining: the class leaves My classes and goes back to Find classes, its lessons close,
     * and joining again brings back everything the learner did in it.
     */
    @Test
    void leavingAClassKeepsTheProgressForJoiningAgain() throws Exception {
        PublishedClass published = api.publishedClass(api.adminToken(), "Leave class " + uniqueSuffix());
        String studentToken = api.registerStudent(uniqueEmail("leave-student"));
        api.join(studentToken, published.classId());
        long attemptId = api.data(api.submitQuiz(studentToken, published.quizId(), false, "hǎo")).path("attemptId").asLong();
        JsonNode lesson = api.data(mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andReturn());
        long flashcardsId = lesson.path("activities").get(0).path("id").asLong();
        mockMvc.perform(api.authPost(STUDENT + "/activities/FLASHCARDS/" + flashcardsId + "/complete", studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(api.authPost(STUDENT + "/" + published.classId() + "/leave", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You left the class."));

        mockMvc.perform(api.authGet(STUDENT, studentToken))
                .andExpect(jsonPath("$.data.classes.length()").value(0));
        mockMvc.perform(api.authGet(STUDENT + "/catalogue?size=100", studentToken))
                .andExpect(jsonPath("$.data.items[?(@.id == " + published.classId() + ")]").isNotEmpty());
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), studentToken))
                .andExpect(jsonPath("$.data.joined").value(false));
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andExpect(status().isConflict());
        mockMvc.perform(api.authGet(STUDENT + "/quiz-attempts/" + attemptId, studentToken))
                .andExpect(status().isConflict());
        // Leaving again is harmless.
        mockMvc.perform(api.authPost(STUDENT + "/" + published.classId() + "/leave", studentToken))
                .andExpect(status().isOk());

        api.join(studentToken, published.classId());
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepsDone").value(2))
                .andExpect(jsonPath("$.data.quizzes[0].attemptCount").value(1))
                .andExpect(jsonPath("$.data.quizzes[0].bestPercent").value(80))
                .andExpect(jsonPath("$.data.quizzes[0].bestAttemptId").value(attemptId));
        mockMvc.perform(api.authGet(STUDENT + "/quiz-attempts/" + attemptId, studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void activitiesAndTheLessonQuizDriveLessonAndClassProgress() throws Exception {
        String adminToken = api.adminToken();
        Long classId = api.draftClass(adminToken, "Progress class " + uniqueSuffix());
        Long unitId = api.unit(adminToken, classId, "Unit 1");
        Long secondUnitId = api.unit(adminToken, classId, "Unit 2");
        Long lessonId = api.draftLesson(adminToken, unitId, "Family");
        JsonNode ready = api.readyLesson(adminToken, lessonId, "Family words");
        String editor = "/api/v1/admin/lessons/" + lessonId;
        Map<String, Long> editorIds = activityIds(ready);
        String matchingEditor = editor + "/activities/MATCHING/" + editorIds.get("MATCHING");
        mockMvc.perform(api.jsonPut(matchingEditor, adminToken, Map.of(
                        "type", "MATCHING", "title", "Match the family",
                        "pairs", List.of(
                                Map.of("chinese", "妈妈", "pinyin", "māma", "meaning", "ＡＢ"),
                                Map.of("chinese", "爸爸", "pinyin", "bàba", "meaning", "a b")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Each meaning can only be used once."));
        mockMvc.perform(api.jsonPut(matchingEditor, adminToken, Map.of(
                        "type", "MATCHING", "title", "Match the family",
                        "pairs", List.of(
                                Map.of("chinese", "妈妈", "pinyin", "māma", "meaning", "mum"),
                                Map.of("chinese", "爸爸", "pinyin", "bàba", "meaning", "dad")))))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPut(editor + "/activities/TRACING/" + editorIds.get("TRACING"), adminToken, Map.of(
                        "type", "TRACING", "title", "Trace 家",
                        "characters", List.of(Map.of("character", "家", "pinyin", "jiā", "meaning", "home")))))
                .andExpect(status().isOk());
        api.publishLesson(adminToken, lessonId);
        long secondLessonId = api.publishedLesson(adminToken, unitId, "Family 2", "More family words", true, null)
                .path("lessonId").asLong();
        api.publishedLesson(adminToken, secondUnitId, "Family 3", "Family at home", true, null);
        api.publishedLesson(adminToken, secondUnitId, "Family 4", "Family at work", true, null);
        api.publishClass(adminToken, classId);

        String studentToken = api.registerStudent(uniqueEmail("progress-student"));
        api.join(studentToken, classId);
        JsonNode lesson = api.data(mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities.length()").value(3))
                .andExpect(jsonPath("$.data.stepsTotal").value(4))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andReturn());
        Map<String, Long> activityIds = activityIds(lesson);
        long quizId = lesson.path("quizzes").get(0).path("id").asLong();

        assertEmptyActivitiesLeaveProgressUntouched(studentToken, lessonId, secondLessonId, activityIds);
        // Restoring the legacy cards' key phrases and the tracing character makes both playable again.
        mockMvc.perform(api.authGet(STUDENT + "/activities/flashcards/" + activityIds.get("FLASHCARDS"), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classId").value(classId))
                .andExpect(jsonPath("$.data.lessonId").value(lessonId))
                .andExpect(jsonPath("$.data.cards[0].chinese").value("你好"))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.hintsAllowed").value(true))
                .andExpect(jsonPath("$.data.next.type").value("TRACING"));
        mockMvc.perform(api.authPost(STUDENT + "/activities/FLASHCARDS/" + activityIds.get("FLASHCARDS") + "/complete", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonStatus").value("IN_PROGRESS"));
        assertStepsDone(studentToken, lessonId, 1);
        mockMvc.perform(api.authPost(STUDENT + "/activities/TRACING/" + activityIds.get("TRACING") + "/complete", studentToken))
                .andExpect(status().isOk());
        assertStepsDone(studentToken, lessonId, 2);

        // Matching is checked on the server: it cannot be marked done, and wrong pairs are reported.
        long matchingId = activityIds.get("MATCHING");
        String matchingPath = STUDENT + "/activities/matching/" + matchingId;
        mockMvc.perform(api.authPost(STUDENT + "/activities/MATCHING/" + matchingId + "/complete", studentToken))
                .andExpect(status().isBadRequest());
        JsonNode play = api.data(mockMvc.perform(api.authGet(matchingPath, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.words.length()").value(2))
                .andExpect(jsonPath("$.data.meanings.length()").value(2))
                .andReturn());
        long mumId = wordId(play, "妈妈");
        long dadId = wordId(play, "爸爸");
        mockMvc.perform(api.jsonPost(matchingPath + "/check", studentToken, Map.of("pairs", List.of(
                        new ChosenPair(mumId, null), new ChosenPair(mumId, "mum"), new ChosenPair(dadId, "dad")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose one meaning per word."));
        assertStepsDone(studentToken, lessonId, 2);
        mockMvc.perform(api.jsonPost(matchingPath + "/hint", studentToken, Map.of("wordId", dadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meaning").value("dad"));
        mockMvc.perform(api.jsonPost(matchingPath + "/check", studentToken, Map.of("pairs", List.of(
                        Map.of("wordId", mumId, "meaning", "dad"), Map.of("wordId", dadId, "meaning", "mum")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.pairs[?(@.correct == false)].wordId").isNotEmpty());
        assertStepsDone(studentToken, lessonId, 2);
        mockMvc.perform(api.jsonPost(matchingPath + "/check", studentToken, Map.of("pairs", List.of(
                        Map.of("wordId", mumId, "meaning", "mum"), Map.of("wordId", dadId, "meaning", "dad")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true));
        assertStepsDone(studentToken, lessonId, 3);

        // A quiz with one wrong answer completes the lesson; a perfect retake improves the best score.
        JsonNode result = api.data(api.submitQuiz(studentToken, quizId, false, "hǎo"));
        assertThat(result.path("percent").asInt()).isEqualTo(80);
        assertThat(result.path("bestPercent").asInt()).isEqualTo(80);
        assertThat(result.path("bestCorrect").asInt()).isEqualTo(4);
        assertThat(result.path("bestTotal").asInt()).isEqualTo(5);
        assertThat(result.path("wrongCount").asInt()).isEqualTo(1);
        assertThat(result.path("lessonStatus").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("questions").findValuesAsText("correct"))
                .containsExactly("false", "true", "true", "true", "true");
        assertThat(result.path("questions").get(0).path("explanation").asText()).isEqualTo("你好 is the answer.");
        assertThat(result.has("feedback")).isFalse();
        long firstAttemptId = result.path("attemptId").asLong();
        result = api.data(api.submitQuiz(studentToken, quizId, true, "hǎo"));
        long bestAttemptId = result.path("attemptId").asLong();
        assertThat(result.path("wrongCount").asInt()).isZero();
        assertThat(result.path("bestPercent").asInt()).isEqualTo(100);
        assertThat(result.path("lessonStatus").asText()).isEqualTo("COMPLETED");

        mockMvc.perform(api.authGet(STUDENT + "/" + classId, studentToken))
                .andExpect(jsonPath("$.data.completedLessons").value(1))
                .andExpect(jsonPath("$.data.summary.lessonCount").value(4))
                .andExpect(jsonPath("$.data.nextStep.lessonTitle").value("Family 2"));
        // Both submitted attempts count; the best one is the perfect retake.
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].attemptCount").value(2))
                .andExpect(jsonPath("$.data.quizzes[0].bestAttemptId").value(bestAttemptId));
        assertThat(firstAttemptId).isNotEqualTo(bestAttemptId);
        mockMvc.perform(api.authGet("/api/v1/students/rewards", studentToken))
                .andExpect(jsonPath("$.data.totalPoints").value(0));

        // A weaker retake says its own score and names the best one: "Your best is 5 out of 5 (100%)".
        result = api.data(api.submitQuiz(studentToken, quizId, false, "hǎo"));
        assertThat(result.path("percent").asInt()).isEqualTo(80);
        assertThat(result.path("correctCount").asInt()).isEqualTo(4);
        assertThat(result.path("bestPercent").asInt()).isEqualTo(100);
        assertThat(result.path("bestCorrect").asInt()).isEqualTo(5);
        assertThat(result.path("bestTotal").asInt()).isEqualTo(5);

        // An earlier weighted attempt had four saved answers, three right, and scored 9 of 10 points.
        jdbc.update("delete from quiz_attempt_answers where quiz_attempt_id = ? order by id limit 1", bestAttemptId);
        jdbc.update("update quiz_attempt_answers set correct = false where quiz_attempt_id = ? order by id limit 1", bestAttemptId);
        jdbc.update("update quiz_attempts set score = 9, max_score = 10 where id = ?", bestAttemptId);
        String resultPath = STUDENT + "/quiz-attempts/" + firstAttemptId;
        mockMvc.perform(api.authGet(resultPath, studentToken))
                .andExpect(jsonPath("$.data.bestPercent").value(90))
                .andExpect(jsonPath("$.data.bestCorrect").value(3))
                .andExpect(jsonPath("$.data.bestTotal").value(4));
        // An old attempt may retain its points without any answer rows.
        jdbc.update("delete from quiz_attempt_answers where quiz_attempt_id = ?", bestAttemptId);
        mockMvc.perform(api.authGet(resultPath, studentToken))
                .andExpect(jsonPath("$.data.bestPercent").value(90))
                .andExpect(jsonPath("$.data.bestCorrect").value(0))
                .andExpect(jsonPath("$.data.bestTotal").value(0));
    }

    @Test
    void quizAnswersAreSavedOneByOneWithHintsAndTheResultFollowsTheSubmit() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass published = api.publishedClassWithQuiz(adminToken, "Quiz flow class " + uniqueSuffix(), true,
                "It starts with 你.");
        Long hintedQuiz = published.quizId();
        // Each weight fits the question's INT column. Their sum needs a wider attempt total.
        jdbc.update("update quiz_questions set points = ? where quiz_id = ?", 1_000_000_000, hintedQuiz);
        mockMvc.perform(api.authGet("/api/v1/admin/lessons/" + published.lessonId(), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes.length()").value(1))
                .andExpect(jsonPath("$.data.quizzes[0].hintsAllowed").value(true))
                .andExpect(jsonPath("$.data.quizzes[0].questions.length()").value(5))
                .andExpect(jsonPath("$.data.quizzes[0].learningStage").doesNotExist());
        String studentToken = api.registerStudent(uniqueEmail("quiz-flow-student"));

        JsonNode session = api.startQuiz(studentToken, hintedQuiz);
        long attemptId = session.path("attemptId").asLong();
        assertThat(session.path("quiz").path("id").asLong()).isEqualTo(hintedQuiz);
        JsonNode questions = session.path("questions");
        assertThat(questions).hasSize(5);
        JsonNode first = questions.get(0);
        Map<String, Object> firstAnswer = option(first, 0);
        assertThat(first.path("hasHint").asBoolean()).isTrue();
        assertThat(first.path("options").get(0).has("correct")).isFalse();

        mockMvc.perform(api.jsonPost(STUDENT + "/quiz-attempts/" + attemptId + "/hint", studentToken,
                        Map.of("questionId", first.path("id").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint").value("It starts with 你."));
        mockMvc.perform(api.saveAnswer(studentToken, attemptId, firstAnswer)).andExpect(status().isOk());

        // Resuming returns the same attempt with the saved answer.
        JsonNode resumed = api.startQuiz(studentToken, hintedQuiz);
        assertThat(resumed.path("attemptId").asLong()).isEqualTo(attemptId);
        assertThat(resumed.path("answers")).hasSize(1);

        long typedQuestionId = questions.get(1).path("id").asLong();
        String typedAnswerPath = STUDENT + "/quiz-attempts/" + attemptId + "/answers/" + typedQuestionId;
        mockMvc.perform(api.saveAnswer(studentToken, attemptId, Map.of("questionId", typedQuestionId, "answerText", "draft")))
                .andExpect(status().isOk());
        assertThat(api.startQuiz(studentToken, hintedQuiz).path("answers")).hasSize(2);
        mockMvc.perform(api.authDelete(typedAnswerPath, studentToken)).andExpect(status().isOk());
        mockMvc.perform(api.authDelete(typedAnswerPath, studentToken)).andExpect(status().isOk());
        JsonNode remainingAnswers = api.startQuiz(studentToken, hintedQuiz).path("answers");
        assertThat(remainingAnswers).hasSize(1);
        assertThat(remainingAnswers.get(0).path("questionId").asLong()).isEqualTo(first.path("id").asLong());

        // Another learner can't touch the attempt.
        String otherToken = api.registerStudent(uniqueEmail("quiz-flow-other"));
        api.join(otherToken, published.classId());
        mockMvc.perform(api.saveAnswer(otherToken, attemptId, firstAnswer)).andExpect(status().isNotFound());

        // The result opens once the attempt is submitted, and a submitted attempt takes no more answers.
        mockMvc.perform(api.authGet(STUDENT + "/quiz-attempts/" + attemptId, studentToken))
                .andExpect(status().isConflict());
        // As on the quiz page, an attempt is submitted with every question answered: an empty one is no lesson step.
        mockMvc.perform(api.authPost(STUDENT + "/quiz-attempts/" + attemptId + "/submit", studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Answer every question before you submit."));
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andExpect(jsonPath("$.data.quizzes[0].attemptCount").value(0))
                .andExpect(jsonPath("$.data.quizzes[0].inProgress").value(true));
        mockMvc.perform(api.saveAnswer(studentToken, attemptId,
                        Map.of("questionId", questions.get(1).path("id").asLong(), "answerText", "nǐ")))
                .andExpect(status().isOk());
        mockMvc.perform(api.saveAnswer(studentToken, attemptId, option(questions.get(2), 1))).andExpect(status().isOk());
        mockMvc.perform(api.saveAnswer(studentToken, attemptId, option(questions.get(3), 0))).andExpect(status().isOk());
        mockMvc.perform(api.saveAnswer(studentToken, attemptId, option(questions.get(4), 0))).andExpect(status().isOk());
        mockMvc.perform(api.authPost(STUDENT + "/quiz-attempts/" + attemptId + "/submit", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bestPercent").value(60))
                .andExpect(jsonPath("$.data.correctCount").value(3))
                .andExpect(jsonPath("$.data.wrongCount").value(2));
        assertThat(attemptRepository.findById(attemptId).orElseThrow()).satisfies(attempt -> {
            assertThat(attempt.getScore()).isEqualTo(3_000_000_000L);
            assertThat(attempt.getMaxScore()).isEqualTo(5_000_000_000L);
        });
        mockMvc.perform(api.authGet(STUDENT + "/quiz-attempts/" + attemptId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quiz.id").value(hintedQuiz))
                .andExpect(jsonPath("$.data.correctCount").value(3));
        mockMvc.perform(api.saveAnswer(studentToken, attemptId, firstAnswer)).andExpect(status().isConflict());
        mockMvc.perform(api.authDelete(typedAnswerPath, studentToken)).andExpect(status().isConflict());
    }

    /**
     * "Leave quiz" discards the open attempt with its saved answers; the next start is a new attempt. A submitted
     * attempt is never discarded, and discarding one already gone or belonging to another learner returns the same
     * idempotent OK response without modifying it.
     */
    @Test
    void leavingAQuizMidwayDiscardsTheOpenAttempt() throws Exception {
        PublishedClass published = api.publishedClass(api.adminToken(), "Discard class " + uniqueSuffix());
        String studentToken = api.registerStudent(uniqueEmail("discard-student"));
        JsonNode session = api.startQuiz(studentToken, published.quizId());
        long attemptId = session.path("attemptId").asLong();
        mockMvc.perform(api.saveAnswer(studentToken, attemptId, option(session.path("questions").get(0), 0)))
                .andExpect(status().isOk());
        String otherToken = api.registerStudent(uniqueEmail("discard-other"));
        api.join(otherToken, published.classId());
        JsonNode otherSession = api.startQuiz(otherToken, published.quizId());
        long otherAttemptId = otherSession.path("attemptId").asLong();
        mockMvc.perform(api.saveAnswer(otherToken, otherAttemptId, option(otherSession.path("questions").get(0), 0)))
                .andExpect(status().isOk());

        // Discarding another learner's attempt has no effect and returns the same idempotent response as a missing id
        // so attempt ids cannot be probed across accounts.
        mockMvc.perform(api.authDelete(STUDENT + "/quiz-attempts/" + attemptId, otherToken))
                .andExpect(status().isOk());
        assertThat(attemptRepository.findById(attemptId)).isPresent();
        mockMvc.perform(api.authDelete(STUDENT + "/quiz-attempts/" + attemptId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Quiz attempt discarded."));

        assertThat(attemptRepository.findById(attemptId)).isEmpty();
        assertThat(answerRepository.findByQuizAttemptIdOrderByCreatedAtAscIdAsc(attemptId)).isEmpty();
        assertThat(api.startQuiz(otherToken, published.quizId()).path("answers")).hasSize(1);
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andExpect(jsonPath("$.data.quizzes[0].inProgress").value(false))
                .andExpect(jsonPath("$.data.quizzes[0].attemptCount").value(0));
        // Sent again (a retried request): already gone, nothing to do.
        mockMvc.perform(api.authDelete(STUDENT + "/quiz-attempts/" + attemptId, studentToken))
                .andExpect(status().isOk());

        JsonNode fresh = api.startQuiz(studentToken, published.quizId());
        assertThat(fresh.path("attemptId").asLong()).isNotEqualTo(attemptId);
        assertThat(fresh.path("answers")).isEmpty();
        long submittedId = api.data(api.submitQuiz(studentToken, published.quizId(), true, "hǎo")).path("attemptId").asLong();
        assertThat(submittedId).isEqualTo(fresh.path("attemptId").asLong());
        mockMvc.perform(api.authDelete(STUDENT + "/quiz-attempts/" + submittedId, studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This quiz attempt has already been submitted."));
        mockMvc.perform(api.authGet(STUDENT + "/quiz-attempts/" + submittedId, studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void watchingAVideoStartsOnlyThatLearnersLessonWithoutCompletingAStep() throws Exception {
        PublishedClass published = api.publishedClass(api.adminToken(), "Video progress class " + uniqueSuffix());
        Long classId = published.classId();
        Long lessonId = published.lessonId();
        String watcher = api.registerStudent(uniqueEmail("video-watcher"));
        String classmate = api.registerStudent(uniqueEmail("video-classmate"));
        api.join(watcher, classId);
        api.join(classmate, classId);
        mockMvc.perform(api.authPost(STUDENT + "/lessons/" + lessonId + "/video/watched", watcher))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, watcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.video.title").value("Saying hello"))
                .andExpect(jsonPath("$.data.video.durationSeconds").value(280))
                .andExpect(jsonPath("$.data.video.streamPath").isString())
                .andExpect(jsonPath("$.data.video.watched").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.stepsDone").value(0));
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, classmate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.video.watched").value(false))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
        mockMvc.perform(api.authGet(STUDENT + "/" + classId, watcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].lessons[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completedLessons").value(0));
    }

    /**
     * With "Allow SparkBot hints" off, the lesson offers no hints: no quiz question offers one, the server refuses to
     * give it, and the matching activity has no hint either.
     */
    @Test
    void aLessonWithoutHintsOffersNone() throws Exception {
        PublishedClass published = api.publishedClassWithQuiz(api.adminToken(), "No hints class " + uniqueSuffix(), false,
                "It starts with 你.");
        String studentToken = api.registerStudent(uniqueEmail("no-hints-student"));

        JsonNode session = api.startQuiz(studentToken, published.quizId());
        JsonNode first = session.path("questions").get(0);
        assertThat(first.path("hasHint").asBoolean()).isFalse();
        mockMvc.perform(api.jsonPost(STUDENT + "/quiz-attempts/" + session.path("attemptId").asLong() + "/hint", studentToken,
                        Map.of("questionId", first.path("id").asLong())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Hints are not available in this quiz."));

        JsonNode lesson = api.data(mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andReturn());
        long matchingId = activityIds(lesson).get("MATCHING");
        JsonNode play = api.data(mockMvc.perform(api.authGet(STUDENT + "/activities/matching/" + matchingId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hintsAllowed").value(false))
                .andReturn());
        mockMvc.perform(api.jsonPost(STUDENT + "/activities/matching/" + matchingId + "/hint", studentToken,
                        Map.of("wordId", wordId(play, "你好"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Hints are not available in this activity."));
    }

    /**
     * An admin archives a class: it leaves the catalogue, and the learners who joined keep it read-only on My classes
     * (its lessons, the video listed without a link, their results), until an admin restores it.
     */
    @Test
    void anArchivedClassIsReadOnlyToItsLearners() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass published = api.publishedClass(adminToken, "Archived class " + uniqueSuffix());
        String studentToken = api.registerStudent(uniqueEmail("archived-student"));
        long attemptId = api.data(api.submitQuiz(studentToken, published.quizId(), true, "hǎo")).path("attemptId").asLong();
        JsonNode lesson = api.data(mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andReturn());
        long flashcardsId = lesson.path("activities").get(0).path("id").asLong();

        mockMvc.perform(api.authPost("/api/v1/admin/classes/" + published.classId() + "/archive", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(api.authGet(STUDENT, studentToken))
                .andExpect(jsonPath("$.data.classes[0].archived").value(true))
                .andExpect(jsonPath("$.data.classes[0].archivedAt").isString())
                .andExpect(jsonPath("$.data.classes[0].completedLessons").value(0));
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.archived").value(true))
                .andExpect(jsonPath("$.data.archivedAt").isString());
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + published.lessonId(), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(true))
                .andExpect(jsonPath("$.data.video.title").value("Saying hello"))
                .andExpect(jsonPath("$.data.video.durationSeconds").value(280))
                .andExpect(jsonPath("$.data.video.streamPath").doesNotExist())
                .andExpect(jsonPath("$.data.video.externalUrl").doesNotExist())
                .andExpect(jsonPath("$.data.quizzes[0].bestPercent").value(100));
        mockMvc.perform(api.authGet(STUDENT + "/quiz-attempts/" + attemptId, studentToken)).andExpect(status().isOk());

        // Nothing changes in it: no quiz, no activity, no video progress, and it can't be left.
        mockMvc.perform(api.authPost(STUDENT + "/quizzes/" + published.quizId() + "/attempts", studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ARCHIVED_READ_ONLY));
        mockMvc.perform(api.authPost(STUDENT + "/activities/FLASHCARDS/" + flashcardsId + "/complete", studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ARCHIVED_READ_ONLY));
        mockMvc.perform(api.authPost(STUDENT + "/lessons/" + published.lessonId() + "/video/watched", studentToken))
                .andExpect(status().isConflict());
        mockMvc.perform(api.authPost(STUDENT + "/" + published.classId() + "/leave", studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "An archived class can't be left. You can still open its lessons and your results."));

        // Nobody new finds or joins it.
        String newcomer = api.registerStudent(uniqueEmail("archived-newcomer"));
        mockMvc.perform(api.authGet(STUDENT + "/catalogue?size=100", newcomer))
                .andExpect(jsonPath("$.data.items[?(@.id == " + published.classId() + ")]").isEmpty());
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), newcomer))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authPost(STUDENT + "/" + published.classId() + "/join", newcomer))
                .andExpect(status().isNotFound());

        // Restored, it is open again.
        mockMvc.perform(api.authPost("/api/v1/admin/classes/" + published.classId() + "/restore", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), studentToken))
                .andExpect(jsonPath("$.data.archived").value(false))
                .andExpect(jsonPath("$.data.archivedAt").doesNotExist());
        mockMvc.perform(api.authPost(STUDENT + "/quizzes/" + published.quizId() + "/attempts", studentToken))
                .andExpect(status().isOk());
    }

    private void assertStepsDone(String studentToken, Long lessonId, int stepsDone) throws Exception {
        mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, studentToken))
                .andExpect(jsonPath("$.data.stepsDone").value(stepsDone));
    }

    private void assertEmptyActivitiesLeaveProgressUntouched(
            String token, long lessonId, long otherLessonId, Map<String, Long> activities) throws Exception {
        long phraseId = jdbc.queryForObject("select id from lesson_key_phrases where lesson_id = ?", Long.class, lessonId);
        long tracingId = activities.get("TRACING");
        String characters = jdbc.queryForObject("select target_character from tracing_exercises where id = ?",
                String.class, tracingId);
        // Reproduce an old lesson without material, preserving its original records for the rest of the journey.
        jdbc.update("update lesson_key_phrases set lesson_id = ? where id = ?", otherLessonId, phraseId);
        try {
            jdbc.update("update tracing_exercises set target_character = null where id = ?", tracingId);
            for (String type : List.of("FLASHCARDS", "TRACING")) {
                mockMvc.perform(api.authPost(STUDENT + "/activities/" + type + "/" + activities.get(type) + "/complete", token))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("This activity has no learning material yet."));
            }
            jdbc.update("update tracing_exercises set target_character = ? where id = ?", "\u00a0\u2007\u202f\ufeff", tracingId);
            mockMvc.perform(api.authPost(STUDENT + "/activities/TRACING/" + tracingId + "/complete", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This activity has no learning material yet."));
            assertStepsDone(token, lessonId, 0);
        } finally {
            jdbc.update("update lesson_key_phrases set lesson_id = ? where id = ?", lessonId, phraseId);
            jdbc.update("update tracing_exercises set target_character = ? where id = ?", characters, tracingId);
        }
    }

    private static Map<String, Long> activityIds(JsonNode lesson) {
        Map<String, Long> ids = new HashMap<>();
        lesson.path("activities").forEach(activity -> ids.put(activity.path("type").asText(), activity.path("id").asLong()));
        return ids;
    }

    /** The answer choosing the option at {@code index} (0 is the right one in the test quizzes). */
    private static Map<String, Object> option(JsonNode question, int index) {
        return Map.of("questionId", question.path("id").asLong(),
                "optionId", question.path("options").get(index).path("id").asLong());
    }

    private static long wordId(JsonNode play, String chinese) {
        for (JsonNode word : play.path("words")) {
            if (word.path("chinese").asText().equals(chinese)) {
                return word.path("id").asLong();
            }
        }
        throw new AssertionError("No word " + chinese);
    }
}
