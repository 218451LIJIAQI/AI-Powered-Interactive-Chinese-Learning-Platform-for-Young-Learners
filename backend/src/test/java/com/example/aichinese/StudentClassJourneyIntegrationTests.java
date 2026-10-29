package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A student's way through a teacher-made class: the catalogue, joining (lessons stay closed until then), lesson
 * activities checked on the server, the lesson quiz with saved answers, hints, the time limit and the result, and
 * the progress that follows (completed, mastered, class percent). Classes never give XP.
 */
class StudentClassJourneyIntegrationTests extends ApiIntegrationTest {

    private static final String STUDENT = "/api/v1/students/classes";

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Test
    void catalogueShowsPublishedClassesAndLessonsOpenOnlyAfterJoining() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("journey-teacher"));
        PublishedClass published = api.publishedClass(teacherToken, "Journey class", "First words", "Hello and thanks");
        api.draftClass(teacherToken, "Journey hidden draft");
        String studentEmail = uniqueEmail("journey-student");
        String studentToken = api.registerStudent(studentEmail);

        mockMvc.perform(get("/api/v1/public/classes").param("keyword", "Journey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title == 'Journey class')]").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.title == 'Journey hidden draft')]").isEmpty());
        mockMvc.perform(get("/api/v1/public/classes/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topics").isArray())
                .andExpect(jsonPath("$.data.levels[0].name").isString());
        mockMvc.perform(api.authGet(STUDENT + "/catalogue", studentToken).param("keyword", "Journey class"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(published.classId()));

        // Before joining: a preview, no lessons.
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined").value(false))
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
                .andExpect(jsonPath("$.data.classes[0].nextStep.lessonId").value(published.lessonId()));
        mockMvc.perform(api.authGet(STUDENT + "/" + published.classId(), studentToken))
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.lessonCount").value(1))
                .andExpect(jsonPath("$.data.units[0].lessons[0].status").value("NOT_STARTED"));

        // The teacher hears about the new student once.
        assertThat(teacherNotificationsAbout(studentEmail, teacherToken)).isEqualTo(1);
    }

    @Test
    void activitiesAndTheLessonQuizDriveLessonAndClassProgress() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("progress-teacher"));
        Long classId = api.draftClass(teacherToken, "Progress class");
        Long lessonId = api.draftLesson(teacherToken, api.unit(teacherToken, classId, "Unit 1"), "Family");
        api.readyLesson(teacherToken, lessonId, "Family words");
        String editor = "/api/v1/teachers/lessons/" + lessonId;
        mockMvc.perform(api.jsonPost(editor + "/activities", teacherToken, Map.of(
                        "type", "MATCHING", "title", "Match the family", "stage", "SCAFFOLDED",
                        "pairs", List.of(
                                Map.of("chinese", "妈妈", "pinyin", "māma", "meaning", "mum"),
                                Map.of("chinese", "爸爸", "pinyin", "bàba", "meaning", "dad")))))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(editor + "/activities", teacherToken, Map.of(
                        "type", "TRACING", "title", "Trace 家", "stage", "GUIDED",
                        "characters", List.of(Map.of("character", "家", "pinyin", "jiā", "meaning", "home")))))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(editor + "/submit", teacherToken)).andExpect(status().isOk());
        mockMvc.perform(api.authPost("/api/v1/admin/class-reviews/classes/" + classId + "/approve", api.adminToken()))
                .andExpect(status().isOk());

        String studentToken = api.registerStudent(uniqueEmail("progress-student"));
        api.join(studentToken, classId);
        JsonNode lesson = api.data(mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities.length()").value(3))
                .andExpect(jsonPath("$.data.stepsTotal").value(4))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andReturn());
        Map<String, Long> activityIds = new HashMap<>();
        lesson.path("activities").forEach(activity -> activityIds.put(activity.path("type").asText(), activity.path("id").asLong()));
        long quizId = lesson.path("quizzes").get(0).path("id").asLong();

        // Flashcards and tracing finish when the student says so.
        mockMvc.perform(api.authGet(STUDENT + "/activities/flashcards/" + activityIds.get("FLASHCARDS"), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[0].chinese").value("你好"))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.next.type").value("MATCHING"));
        mockMvc.perform(api.authPost(STUDENT + "/activities/FLASHCARDS/" + activityIds.get("FLASHCARDS") + "/complete", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepsDone").value(1))
                .andExpect(jsonPath("$.data.lessonStatus").value("IN_PROGRESS"));
        mockMvc.perform(api.authPost(STUDENT + "/activities/TRACING/" + activityIds.get("TRACING") + "/complete", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepsDone").value(2));

        // Matching is checked on the server: it cannot be marked done, and wrong pairs are reported.
        long matchingId = activityIds.get("MATCHING");
        mockMvc.perform(api.authPost(STUDENT + "/activities/MATCHING/" + matchingId + "/complete", studentToken))
                .andExpect(status().isBadRequest());
        JsonNode play = api.data(mockMvc.perform(api.authGet(STUDENT + "/activities/matching/" + matchingId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.words.length()").value(2))
                .andExpect(jsonPath("$.data.meanings.length()").value(2))
                .andReturn());
        long mumId = wordId(play, "妈妈");
        long dadId = wordId(play, "爸爸");
        mockMvc.perform(api.jsonPost(STUDENT + "/activities/matching/" + matchingId + "/hint", studentToken, Map.of("wordId", dadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meaning").value("dad"));
        mockMvc.perform(api.jsonPost(STUDENT + "/activities/matching/" + matchingId + "/check", studentToken, Map.of("pairs", List.of(
                        Map.of("wordId", mumId, "meaning", "dad"), Map.of("wordId", dadId, "meaning", "mum")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.pairs[?(@.correct == false)].wordId").isNotEmpty())
                .andExpect(jsonPath("$.data.stepsDone").value(2));
        mockMvc.perform(api.jsonPost(STUDENT + "/activities/matching/" + matchingId + "/check", studentToken, Map.of("pairs", List.of(
                        Map.of("wordId", mumId, "meaning", "mum"), Map.of("wordId", dadId, "meaning", "dad")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true))
                .andExpect(jsonPath("$.data.stepsDone").value(3));

        // A half-right quiz completes the lesson; a perfect retake masters it.
        JsonNode result = api.data(api.submitQuiz(studentToken, quizId, false, "hǎo"));
        assertThat(result.path("percent").asInt()).isEqualTo(66);
        assertThat(result.path("lessonStatus").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("questions").findValuesAsText("correct")).containsExactly("false", "true", "true");
        assertThat(result.path("questions").get(0).path("explanation").asText()).isEqualTo("你好 is the answer.");
        result = api.data(api.submitQuiz(studentToken, quizId, true, "hǎo"));
        assertThat(result.path("percent").asInt()).isEqualTo(100);
        assertThat(result.path("bestPercent").asInt()).isEqualTo(100);
        assertThat(result.path("lessonStatus").asText()).isEqualTo("MASTERED");

        mockMvc.perform(api.authGet(STUDENT + "/" + classId, studentToken))
                .andExpect(jsonPath("$.data.progressPercent").value(100))
                .andExpect(jsonPath("$.data.completedLessons").value(1))
                .andExpect(jsonPath("$.data.quizAttemptCount").value(2));
        mockMvc.perform(api.authGet(STUDENT + "/" + classId + "/quiz-results", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts.length()").value(2))
                .andExpect(jsonPath("$.data.averagePercent").value(83));
        mockMvc.perform(api.authGet("/api/v1/students/rewards", studentToken))
                .andExpect(jsonPath("$.data.totalPoints").value(0));
    }

    @Test
    void quizAnswersAreSavedOneByOneWithHintsAndTheTimeLimitSubmitsTheAttempt() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("quiz-flow-teacher"));
        PublishedClass published = api.publishedClass(teacherToken, "Quiz flow class");
        Long hintedQuiz = api.publishedQuiz(teacherToken, published.lessonId(), "GUIDED", "It starts with 你.");
        String studentToken = api.registerStudent(uniqueEmail("quiz-flow-student"));

        JsonNode session = api.startQuiz(studentToken, hintedQuiz);
        long attemptId = session.path("attemptId").asLong();
        JsonNode first = session.path("questions").get(0);
        assertThat(first.path("hasHint").asBoolean()).isTrue();
        assertThat(first.path("options").get(0).has("correct")).isFalse();

        mockMvc.perform(api.jsonPost(STUDENT + "/quiz-attempts/" + attemptId + "/hint", studentToken,
                        Map.of("questionId", first.path("id").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint").value("It starts with 你."));
        mockMvc.perform(api.saveAnswer(studentToken, attemptId,
                        Map.of("questionId", first.path("id").asLong(), "optionId", first.path("options").get(0).path("id").asLong())))
                .andExpect(status().isOk());

        // Resuming returns the same attempt with the saved answer.
        JsonNode resumed = api.startQuiz(studentToken, hintedQuiz);
        assertThat(resumed.path("attemptId").asLong()).isEqualTo(attemptId);
        assertThat(resumed.path("answers")).hasSize(1);

        // Another student can't touch the attempt.
        String otherToken = api.registerStudent(uniqueEmail("quiz-flow-other"));
        api.join(otherToken, published.classId());
        mockMvc.perform(api.saveAnswer(otherToken, attemptId,
                        Map.of("questionId", first.path("id").asLong(), "optionId", first.path("options").get(0).path("id").asLong())))
                .andExpect(status().isNotFound());

        mockMvc.perform(api.authPost(STUDENT + "/quiz-attempts/" + attemptId + "/submit", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hintsUsed").value(1))
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.maxScore").value(15));

        // The timed quiz of the lesson: an answer after the time limit submits the attempt with what was saved.
        JsonNode timed = api.startQuiz(studentToken, published.quizId());
        assertThat(timed.path("expiresAt").isNull()).isFalse();
        long timedAttemptId = timed.path("attemptId").asLong();
        List<Long> optionIds = new ArrayList<>();
        timed.path("questions").forEach(question -> {
            if (question.path("type").asText().equals("MULTIPLE_CHOICE")) {
                optionIds.add(question.path("id").asLong());
                optionIds.add(question.path("options").get(0).path("id").asLong());
            }
        });
        mockMvc.perform(api.saveAnswer(studentToken, timedAttemptId, Map.of("questionId", optionIds.get(0), "optionId", optionIds.get(1))))
                .andExpect(status().isOk());
        QuizAttempt attempt = quizAttemptRepository.findById(timedAttemptId).orElseThrow();
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(20));
        attempt.setExpiresAt(LocalDateTime.now().minusMinutes(10));
        quizAttemptRepository.save(attempt);

        mockMvc.perform(api.saveAnswer(studentToken, timedAttemptId, Map.of("questionId", optionIds.get(2), "optionId", optionIds.get(3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Time is up. Your quiz was submitted with the answers you saved."));
        assertThat(quizAttemptRepository.findById(timedAttemptId).orElseThrow().isSubmitted()).isTrue();
        mockMvc.perform(api.authGet(STUDENT + "/quiz-attempts/" + timedAttemptId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.correctCount").value(1));
    }

    private static long wordId(JsonNode play, String chinese) {
        for (JsonNode word : play.path("words")) {
            if (word.path("chinese").asText().equals(chinese)) {
                return word.path("id").asLong();
            }
        }
        throw new AssertionError("No word " + chinese);
    }

    private long teacherNotificationsAbout(String studentEmail, String teacherToken) throws Exception {
        JsonNode items = api.data(mockMvc.perform(api.authGet("/api/v1/notifications", teacherToken))
                .andExpect(status().isOk())
                .andReturn()).path("items");
        long count = 0;
        for (JsonNode item : items) {
            if (item.path("message").asText().startsWith("Student " + studentEmail)) {
                count++;
            }
        }
        return count;
    }
}
