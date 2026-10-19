package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.LearningApiTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * W4 learner experience end to end (MockMvc, H2): quiz result completeness and retakes
 * (FR-QUIZ-04/07/08), hints (D8) never offered for INDEPENDENT quizzes, T32 atomic rejection,
 * quiz attempt -> progress -> mastery (D25) for the student and the teacher roster without any
 * commerce, lessons without quizzes, video progress, server-validated practice activities with no
 * answer keys before an attempt, and live-class re-enrolment plus attendance (D26).
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=" + LearningApiTestClient.ADMIN_EMAIL,
        "DEFAULT_ADMIN_PASSWORD=" + LearningApiTestClient.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearnerExperienceIntegrationTests {

    private static final String HINT = "Think of the greeting you use every morning.";
    private static final String MASTERY = "$.data.lessons[?(@.lessonId == %d)].mastery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private QuizAttemptAnswerRepository quizAttemptAnswerRepository;

    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Autowired
    private LiveClassEnrolmentRepository liveClassEnrolmentRepository;

    @Autowired
    private ListeningExerciseRepository listeningExerciseRepository;

    @Autowired
    private TracingExerciseRepository tracingExerciseRepository;

    private LearningApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new LearningApiTestClient(mockMvc, objectMapper, userRepository, teacherProfileRepository);
    }

    @Test
    void quizResultIsCompleteRetakesUpdateMasteryAndTheRosterUsesQuizScoresNotFeedback() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-quiz-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Self-introduction basics", "你好！我叫小明。\nnǐ hǎo! wǒ jiào Xiǎo Míng.");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, "GUIDED", HINT);
        String studentToken = api.registerStudent(LearningApiTestClient.uniqueEmail("w4-quiz-student"));

        JsonNode quiz = api.studentQuiz(studentToken, quizId);
        JsonNode optionQuestion = quiz.path("questions").get(0);
        assertThat(optionQuestion.path("hintAvailable").asBoolean()).isTrue();
        assertThat(optionQuestion.has("hint")).as("hint text is never part of the quiz payload").isFalse();
        assertThat(quiz.path("questions").get(1).path("hintAvailable").asBoolean()).isFalse();
        long optionQuestionId = optionQuestion.path("id").asLong();

        mockMvc.perform(api.authGet("/api/v1/students/quizzes/" + quizId + "/questions/" + optionQuestionId + "/hint", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint").value(HINT));
        mockMvc.perform(api.authGet("/api/v1/students/quizzes/" + quizId + "/questions/"
                        + quiz.path("questions").get(1).path("id").asLong() + "/hint", studentToken))
                .andExpect(status().isNotFound());

        // First attempt: wrong option, wrong tone, hint used on the first question.
        long wrongOptionId = optionQuestion.path("options").get(1).path("id").asLong();
        long fillQuestionId = quiz.path("questions").get(1).path("id").asLong();
        MvcResult first = mockMvc.perform(api.jsonPost("/api/v1/students/quizzes/" + quizId + "/attempts", studentToken, Map.of(
                        "answers", List.of(
                                Map.of("questionId", optionQuestionId, "optionId", wrongOptionId, "hintUsed", true),
                                Map.of("questionId", fillQuestionId, "answerText", "hao")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.percentage").value(0))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.learningStage").value("GUIDED"))
                .andExpect(jsonPath("$.data.answers[0].selectedOptionId").value(wrongOptionId))
                .andExpect(jsonPath("$.data.answers[0].correctOptionId").value(optionQuestion.path("options").get(0).path("id").asLong()))
                .andExpect(jsonPath("$.data.answers[0].correctAnswer").value("你好"))
                .andExpect(jsonPath("$.data.answers[0].explanation").value("你好 (nǐ hǎo) means hello."))
                .andExpect(jsonPath("$.data.answers[0].hintUsed").value(true))
                .andExpect(jsonPath("$.data.answers[0].options.length()").value(2))
                .andExpect(jsonPath("$.data.answers[1].correctAnswer").value("hǎo"))
                .andReturn();
        long firstAttemptId = api.data(first).path("attemptId").asLong();
        assertThat(quizAttemptAnswerRepository.countByQuizAttemptIdAndHintUsedTrue(firstAttemptId)).isEqualTo(1);

        mockMvc.perform(api.authGet("/api/v1/students/wrong-answers", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].lessonTitle").value("Self-introduction basics"))
                .andExpect(jsonPath("$.data.items[0].quizId").value(quizId));
        mockMvc.perform(api.authGet("/api/v1/students/progress", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(MASTERY.formatted(lessonId)).value("COMPLETED"))
                .andExpect(jsonPath("$.data.weakAreas[0]").value("Self-introduction basics"));

        MvcResult rosterAfterLowScore = mockMvc.perform(api.authGet("/api/v1/teachers/students", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].needsSupport").value(true))
                .andExpect(jsonPath("$.data.items[0].supportReasons[0]").value("LOW_QUIZ_SCORE"))
                .andExpect(jsonPath("$.data.items[0].weakAreas[0]").value("Self-introduction basics"))
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
                .andReturn();
        long studentProfileId = api.data(rosterAfterLowScore).path("items").get(0).path("studentId").asLong();

        // Retake (FR-QUIZ-07): everything correct -> 100 % and MASTERED.
        mockMvc.perform(api.authGet("/api/v1/students/quizzes/" + quizId, studentToken)).andExpect(status().isOk());
        api.submitQuiz(studentToken, quizId, true, " HǍO ");
        MvcResult retake = api.submitQuiz(studentToken, quizId, true, "hǎo");
        assertThat(retake.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.data(retake).path("percentage").asInt()).isEqualTo(100);
        assertThat(api.data(retake).path("passed").asBoolean()).isTrue();

        mockMvc.perform(api.authGet("/api/v1/students/progress", studentToken))
                .andExpect(jsonPath(MASTERY.formatted(lessonId)).value("MASTERED"))
                .andExpect(jsonPath("$.data.lessons[0].bestQuizPercentage").value(100));
        mockMvc.perform(api.authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mastery").value("MASTERED"))
                .andExpect(jsonPath("$.data.basicContent").value("你好！我叫小明。\nnǐ hǎo! wǒ jiào Xiǎo Míng."))
                .andExpect(jsonPath("$.data.quizzes[0].id").value(quizId));

        // Feedback is the teacher's own action and does not flag the student.
        mockMvc.perform(api.jsonPost("/api/v1/teachers/feedback", teacherToken, Map.of(
                        "studentId", studentProfileId,
                        "lessonId", lessonId,
                        "feedbackType", "WEAK_AREA",
                        "message", "Great improvement on greetings!")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet("/api/v1/teachers/students", teacherToken))
                .andExpect(jsonPath("$.data.items[0].lessonMastery[0].mastery").value("MASTERED"))
                .andExpect(jsonPath("$.data.items[0].needsSupport").value(false))
                .andExpect(jsonPath("$.data.items[0].supportReasons.length()").value(0));

        mockMvc.perform(api.authGet("/api/v1/teachers/quizzes/" + quizId + "/attempts", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.attemptId == " + firstAttemptId + ")].hintsUsed").value(1))
                .andExpect(jsonPath("$.data.items[0].percentage").value(100));
    }

    @Test
    void independentQuizNeverOffersOrRecordsHints() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-indep-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Ordering food", "我要一碗面。");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, "INDEPENDENT", HINT);
        String studentToken = api.registerStudent(LearningApiTestClient.uniqueEmail("w4-indep-student"));

        JsonNode quiz = api.studentQuiz(studentToken, quizId);
        long questionId = quiz.path("questions").get(0).path("id").asLong();
        assertThat(quiz.path("questions").get(0).path("hintAvailable").asBoolean()).isFalse();

        mockMvc.perform(api.authGet("/api/v1/students/quizzes/" + quizId + "/questions/" + questionId + "/hint", studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hints are not available for independent practice."));

        MvcResult attempt = mockMvc.perform(api.jsonPost("/api/v1/students/quizzes/" + quizId + "/attempts", studentToken, Map.of(
                        "answers", List.of(
                                Map.of("questionId", questionId,
                                        "optionId", quiz.path("questions").get(0).path("options").get(0).path("id").asLong(),
                                        "hintUsed", true),
                                Map.of("questionId", quiz.path("questions").get(1).path("id").asLong(), "answerText", "hǎo")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].hintUsed").value(false))
                .andReturn();
        assertThat(quizAttemptAnswerRepository.countByQuizAttemptIdAndHintUsedTrue(api.data(attempt).path("attemptId").asLong()))
                .isZero();
    }

    @Test
    void rejectedQuizSubmissionLeavesNoPartialAttemptAnswersPointsOrProgress() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-t32-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Numbers", "一 二 三");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);
        String studentEmail = LearningApiTestClient.uniqueEmail("w4-t32-student");
        String studentToken = api.registerStudent(studentEmail);
        StudentProfile student = student(studentEmail);
        JsonNode quiz = api.studentQuiz(studentToken, quizId);
        long optionQuestionId = quiz.path("questions").get(0).path("id").asLong();
        long fillQuestionId = quiz.path("questions").get(1).path("id").asLong();
        long attemptsBefore = quizAttemptRepository.count();
        long answersBefore = quizAttemptAnswerRepository.count();

        // Missing the second answer.
        mockMvc.perform(api.jsonPost("/api/v1/students/quizzes/" + quizId + "/attempts", studentToken, Map.of(
                        "answers", List.of(Map.of("questionId", optionQuestionId,
                                "optionId", quiz.path("questions").get(0).path("options").get(0).path("id").asLong())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every quiz question requires an answer."));
        // Option question answered with text only (option ids are required since W4).
        mockMvc.perform(api.jsonPost("/api/v1/students/quizzes/" + quizId + "/attempts", studentToken, Map.of(
                        "answers", List.of(
                                Map.of("questionId", optionQuestionId, "answerText", "你好"),
                                Map.of("questionId", fillQuestionId, "answerText", "hǎo")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose one of the listed options for every multiple-choice question."));
        // An option id of another question.
        mockMvc.perform(api.jsonPost("/api/v1/students/quizzes/" + quizId + "/attempts", studentToken, Map.of(
                        "answers", List.of(
                                Map.of("questionId", optionQuestionId, "optionId", 987_654L),
                                Map.of("questionId", fillQuestionId, "answerText", "hǎo")))))
                .andExpect(status().isBadRequest());

        assertThat(quizAttemptRepository.count()).isEqualTo(attemptsBefore);
        assertThat(quizAttemptAnswerRepository.count()).isEqualTo(answersBefore);
        assertThat(pointsTransactionRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())).isEmpty();
        assertThat(student(studentEmail).getTotalPoints()).isZero();
        mockMvc.perform(api.authGet("/api/v1/students/progress", studentToken))
                .andExpect(jsonPath("$.data.totalLessonsStarted").value(0));
    }

    @Test
    void lessonWithoutQuizzesCompletesFromContentAndVideosAndVideoProgressStartsAtZero() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-video-teacher"));
        Long textLessonId = api.publishedLesson(teacherToken, "Campus words", "图书馆 túshūguǎn - library");
        Long videoLessonId = api.publishedLesson(teacherToken, "Travel phrases", "火车站 huǒchēzhàn - train station");
        MvcResult videoResult = mockMvc.perform(multipart("/api/v1/teachers/lessons/" + videoLessonId + "/recorded-videos")
                        .param("title", "Travel phrases video")
                        .param("sourceType", "EXTERNAL_URL")
                        .param("externalVideoUrl", "https://video.example.com/travel")
                        .param("durationSeconds", "120")
                        .param("sortOrder", "1")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, LearningApiTestClient.bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        long videoId = api.id(videoResult);
        String studentToken = api.registerStudent(LearningApiTestClient.uniqueEmail("w4-video-student"));

        mockMvc.perform(api.authGet("/api/v1/students/lessons/" + textLessonId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mastery").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.contentViewed").value(false))
                .andExpect(jsonPath("$.data.quizzes.length()").value(0));
        mockMvc.perform(api.authPost("/api/v1/students/lessons/" + textLessonId + "/viewed", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completionPercent").value(100))
                .andExpect(jsonPath("$.data.mastery").value("COMPLETED"));

        // Video progress before the first save returns a zero record instead of HTTP 500.
        mockMvc.perform(api.authGet("/api/v1/students/video-progress/" + videoId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.progressPercentage").value(0))
                .andExpect(jsonPath("$.data.completed").value(false));

        mockMvc.perform(api.authPost("/api/v1/students/lessons/" + videoLessonId + "/viewed", studentToken))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.completionPercent").value(50))
                .andExpect(jsonPath("$.data.mastery").value("IN_PROGRESS"));
        mockMvc.perform(api.authPost("/api/v1/students/video-progress/" + videoId + "/complete", studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet("/api/v1/students/progress", studentToken))
                .andExpect(jsonPath(MASTERY.formatted(videoLessonId)).value("COMPLETED"))
                .andExpect(jsonPath(MASTERY.formatted(textLessonId)).value("COMPLETED"));
        mockMvc.perform(api.authGet("/api/v1/teachers/lessons/" + videoLessonId + "/video-progress", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].completed").value(true))
                .andExpect(jsonPath("$.data.items[0].studentEmail").doesNotExist());
    }

    @Test
    void practiceActivitiesSendNoAnswerKeysAndAwardXpOnlyForCorrectServerCheckedAnswers() throws Exception {
        String adminToken = api.adminToken();
        String studentEmail = LearningApiTestClient.uniqueEmail("w4-activity-student");
        String studentToken = api.registerStudent(studentEmail);

        // Story: answer key kept on the server (correctOptionIndex), explanations only after passing.
        long storyId = createAndPublish(adminToken, "story-activities", Map.of(
                "title", "At the train station",
                "storyText", "小李在火车站。他说：我要一张票。",
                "xpReward", 20,
                "questions", List.of(Map.of(
                        "questionText", "Where is Xiao Li?",
                        "optionsPayload", "[\"At the train station\",\"At the library\"]",
                        "explanation", "The story says 小李在火车站.",
                        "sortOrder", 0,
                        "correctOptionIndex", 0))));
        MvcResult story = mockMvc.perform(api.authGet("/api/v1/students/activities/story-activities/" + storyId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].options[1].text").value("At the library"))
                .andExpect(jsonPath("$.data.questions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].optionsPayload").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].correctOptionIndex").doesNotExist())
                .andReturn();
        long storyQuestionId = api.data(story).path("questions").get(0).path("id").asLong();
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/story-activities/" + storyId + "/complete", studentToken,
                        Map.of("answers", List.of(Map.of("questionId", storyQuestionId, "optionIndex", 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.earnedPoints").value(0))
                .andExpect(jsonPath("$.data.results[0].explanation").doesNotExist());
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/story-activities/" + storyId + "/complete", studentToken,
                        Map.of("answers", List.of(Map.of("questionId", storyQuestionId, "optionIndex", 0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.earnedPoints").value(20))
                .andExpect(jsonPath("$.data.results[0].explanation").value("The story says 小李在火车站."));

        // Drag-and-drop: items without target zones, zones listed separately.
        long dragId = createAndPublish(adminToken, "drag-drop-activities", Map.of(
                "title", "Sort the words",
                "xpReward", 15,
                "items", List.of(
                        Map.of("dragText", "米饭", "targetZone", "Food", "sortOrder", 0),
                        Map.of("dragText", "火车", "targetZone", "Transport", "sortOrder", 1))));
        MvcResult drag = mockMvc.perform(api.authGet("/api/v1/students/activities/drag-drop-activities/" + dragId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].targetZone").doesNotExist())
                .andExpect(jsonPath("$.data.zones[0]").value("Food"))
                .andExpect(jsonPath("$.data.zones[1]").value("Transport"))
                .andReturn();
        Map<String, Long> itemIds = new java.util.HashMap<>();
        api.data(drag).path("items").forEach(item -> itemIds.put(item.path("dragText").asText(), item.path("id").asLong()));
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/drag-drop-activities/" + dragId + "/complete", studentToken,
                        Map.of("placements", List.of(
                                Map.of("itemId", itemIds.get("米饭"), "zone", "Transport"),
                                Map.of("itemId", itemIds.get("火车"), "zone", "Transport")))))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.correctCount").value(1));
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/drag-drop-activities/" + dragId + "/complete", studentToken,
                        Map.of("placements", List.of(
                                Map.of("itemId", itemIds.get("米饭"), "zone", "food"),
                                Map.of("itemId", itemIds.get("火车"), "zone", "Transport")))))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.earnedPoints").value(15));

        // Listening: a free-text payload cannot be published (no answer key); a structured one hides the answer.
        long freeTextListening = create(adminToken, "listening-exercises", Map.of(
                "title", "Listen for greetings",
                "audioUrl", "https://audio.example.com/greetings.mp3",
                "questionPayload", "Listen and choose: 你好, 再见."));
        mockMvc.perform(api.jsonPatch("/api/v1/admin/activities/listening-exercises/" + freeTextListening + "/status",
                        adminToken, Map.of("status", "PUBLISHED")))
                .andExpect(status().isBadRequest());
        long listeningId = createAndPublish(adminToken, "listening-exercises", Map.of(
                "title", "Listen and choose",
                "audioUrl", "https://audio.example.com/hello.mp3",
                "xpReward", 10,
                "questionPayload", "{\"prompt\":\"Listen.\",\"questions\":[{\"question\":\"What did you hear?\","
                        + "\"options\":[\"你好\",\"再见\"],\"answer\":\"你好\"}]}"));
        mockMvc.perform(api.authGet("/api/v1/students/activities/listening-exercises/" + listeningId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prompt").value("Listen."))
                .andExpect(jsonPath("$.data.questions[0].options[0]").value("你好"))
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data.questionPayload").doesNotExist())
                .andExpect(jsonPath("$.data.freeResponse").value(false));
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/listening-exercises/" + listeningId + "/complete", studentToken,
                        Map.of("answers", List.of(Map.of("questionIndex", 0, "answerText", "再见")))))
                .andExpect(jsonPath("$.data.passed").value(false));
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/listening-exercises/" + listeningId + "/complete", studentToken,
                        Map.of("answers", List.of(Map.of("questionIndex", 0, "answerText", "你好")))))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.earnedPoints").value(10));

        // Legacy rows with optional fields missing no longer return HTTP 500.
        User admin = userRepository.findByEmailWithRoles(LearningApiTestClient.ADMIN_EMAIL).orElseThrow();
        ListeningExercise legacyListening = new ListeningExercise();
        legacyListening.setTitle("Legacy listening");
        legacyListening.setCreatedByUser(admin);
        legacyListening.setContentStatus(ContentStatus.PUBLISHED);
        long legacyListeningId = listeningExerciseRepository.save(legacyListening).getId();
        mockMvc.perform(api.authGet("/api/v1/students/activities/listening-exercises/" + legacyListeningId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioUrl").doesNotExist())
                .andExpect(jsonPath("$.data.freeResponse").value(true));
        TracingExercise tracing = new TracingExercise();
        tracing.setTitle("Trace 人");
        tracing.setCreatedByUser(admin);
        tracing.setContentStatus(ContentStatus.PUBLISHED);
        tracing.setXpReward(12);
        long tracingId = tracingExerciseRepository.save(tracing).getId();
        mockMvc.perform(api.authGet("/api/v1/students/activities/tracing-exercises/" + tracingId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worksheetUrl").doesNotExist());
        // Tracing is practice only: completion needs at least one stroke and is never graded.
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/tracing-exercises/" + tracingId + "/complete", studentToken,
                        Map.of("strokeCount", 0)))
                .andExpect(jsonPath("$.data.passed").value(false));
        mockMvc.perform(api.jsonPost("/api/v1/students/activities/tracing-exercises/" + tracingId + "/complete", studentToken,
                        Map.of("strokeCount", 3)))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.earnedPoints").value(12));

        assertThat(student(studentEmail).getTotalPoints()).isEqualTo(20 + 15 + 10 + 12);
    }

    @Test
    void teacherActivityTextIsScreenedByTheContentSafetyFilter() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-safety-teacher"));

        mockMvc.perform(api.jsonPost("/api/v1/teachers/activities/flashcards", teacherToken, Map.of(
                        "title", "Greetings",
                        "frontText", "你好",
                        "backText", "fuck you")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Content contains language that is not suitable for this learning platform."));
        mockMvc.perform(api.jsonPost("/api/v1/teachers/activities/story-activities", teacherToken, Map.of(
                        "title", "A short story",
                        "storyText", "他说：去死。",
                        "questions", List.of(Map.of(
                                "questionText", "What did he say?",
                                "optionsPayload", "[\"a\",\"b\"]",
                                "correctOptionIndex", 0)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost("/api/v1/teachers/activities/flashcards", teacherToken, Map.of(
                        "title", "Greetings",
                        "frontText", "你好",
                        "backText", "hello")))
                .andExpect(status().isOk());
    }

    @Test
    void studentCanReEnrolAfterCancellingAndJoiningRecordsAttendance() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-live-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Live speaking", "Speaking practice");
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        Long liveClassId = api.id(mockMvc.perform(api.jsonPost("/api/v1/teachers/live-classes", teacherToken, Map.of(
                        "title", "Speaking circle",
                        "description", "Practise self-introductions",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", start.plusHours(1).toString(),
                        "meetingLink", "https://meet.google.com/speaking-circle",
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED",
                        "maxParticipants", 5)))
                .andExpect(status().isOk())
                .andReturn());
        String studentEmail = LearningApiTestClient.uniqueEmail("w4-live-student");
        String studentToken = api.registerStudent(studentEmail);

        mockMvc.perform(api.authPost("/api/v1/students/live-classes/" + liveClassId + "/enrol", studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost("/api/v1/students/live-classes/" + liveClassId + "/cancel-enrolment", studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost("/api/v1/students/live-classes/" + liveClassId + "/enrol", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true));
        mockMvc.perform(api.authPost("/api/v1/students/live-classes/" + liveClassId + "/enrol", studentToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(api.authGet("/api/v1/students/live-classes/" + liveClassId + "/join", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLink").value("https://meet.google.com/speaking-circle"));
        mockMvc.perform(api.authGet("/api/v1/students/live-classes/" + liveClassId + "/join", studentToken))
                .andExpect(status().isOk());

        StudentProfile student = student(studentEmail);
        assertThat(liveClassEnrolmentRepository.findByLiveClassIdOrderByEnrolledAtAsc(liveClassId))
                .filteredOn(enrolment -> enrolment.getStudent().getId().equals(student.getId()))
                .extracting(enrolment -> enrolment.getEnrolmentStatus().name())
                .containsExactlyInAnyOrder("CANCELLED", "ATTENDED");
        mockMvc.perform(api.authGet("/api/v1/teachers/live-classes/" + liveClassId + "/enrolments", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.enrolmentStatus == 'ATTENDED')].attendedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].studentEmail").doesNotExist());
    }

    private long create(String adminToken, String slug, Map<String, Object> body) throws Exception {
        return api.id(mockMvc.perform(api.jsonPost("/api/v1/admin/activities/" + slug, adminToken, body))
                .andExpect(status().isOk())
                .andReturn());
    }

    private long createAndPublish(String adminToken, String slug, Map<String, Object> body) throws Exception {
        long id = create(adminToken, slug, body);
        mockMvc.perform(api.jsonPatch("/api/v1/admin/activities/" + slug + "/" + id + "/status", adminToken,
                        Map.of("status", "PUBLISHED")))
                .andExpect(status().isOk());
        return id;
    }

    private StudentProfile student(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }
}
