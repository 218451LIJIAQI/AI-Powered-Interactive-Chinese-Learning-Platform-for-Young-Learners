package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.LearningApiTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins the results of the batched and paged repository queries that replaced per-row lookups and
 * in-memory paging: children grouped per parent, weak areas ordered by the most recent wrong answer,
 * the teacher's live classes listed upcoming first, and the teacher's quiz attempts paged in the
 * database with the same order and totals.
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=" + LearningApiTestClient.ADMIN_EMAIL,
        "DEFAULT_ADMIN_PASSWORD=" + LearningApiTestClient.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepositoryQueryOptimisationIntegrationTests {

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
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LearningApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new LearningApiTestClient(mockMvc, objectMapper, userRepository, teacherProfileRepository);
    }

    @Test
    void activityListsGroupBatchLoadedChildrenUnderTheirOwnParentInSortOrder() throws Exception {
        String adminToken = api.adminToken();
        String marker = "batch" + UUID.randomUUID().toString().substring(0, 8);

        Long firstGame = createActivity(adminToken, "matching-games", Map.of(
                "title", marker + " game one",
                "items", List.of(
                        Map.of("leftText", "A3", "rightText", "a3", "sortOrder", 3),
                        Map.of("leftText", "A1", "rightText", "a1", "sortOrder", 1),
                        Map.of("leftText", "A2", "rightText", "a2", "sortOrder", 2))));
        Long secondGame = createActivity(adminToken, "matching-games", Map.of(
                "title", marker + " game two",
                "items", List.of(
                        Map.of("leftText", "B2", "rightText", "b2", "sortOrder", 2),
                        Map.of("leftText", "B1", "rightText", "b1", "sortOrder", 1))));
        Long emptyGame = createActivity(adminToken, "matching-games", Map.of(
                "title", marker + " game empty",
                "items", List.of()));
        JsonNode games = listActivities(adminToken, "matching-games", marker);
        assertThat(games.path("totalItems").asLong()).isEqualTo(3);
        assertThat(childTexts(games, firstGame, "items", "leftText")).containsExactly("A1", "A2", "A3");
        assertThat(childTexts(games, secondGame, "items", "leftText")).containsExactly("B1", "B2");
        assertThat(childTexts(games, emptyGame, "items", "leftText")).isEmpty();

        Long firstDragDrop = createActivity(adminToken, "drag-drop-activities", Map.of(
                "title", marker + " sort one",
                "items", List.of(
                        Map.of("dragText", "C2", "targetZone", "zone", "sortOrder", 2),
                        Map.of("dragText", "C1", "targetZone", "zone", "sortOrder", 1))));
        Long secondDragDrop = createActivity(adminToken, "drag-drop-activities", Map.of(
                "title", marker + " sort two",
                "items", List.of(Map.of("dragText", "D1", "targetZone", "zone", "sortOrder", 0))));
        JsonNode dragDrops = listActivities(adminToken, "drag-drop-activities", marker);
        assertThat(childTexts(dragDrops, firstDragDrop, "items", "dragText")).containsExactly("C1", "C2");
        assertThat(childTexts(dragDrops, secondDragDrop, "items", "dragText")).containsExactly("D1");

        Long firstStory = createActivity(adminToken, "story-activities", Map.of(
                "title", marker + " story one",
                "storyText", "小李在火车站。",
                "questions", List.of(
                        storyQuestion("E2", 2),
                        storyQuestion("E1", 1))));
        Long secondStory = createActivity(adminToken, "story-activities", Map.of(
                "title", marker + " story two",
                "storyText", "小王在图书馆。",
                "questions", List.of(storyQuestion("F1", 0))));
        JsonNode stories = listActivities(adminToken, "story-activities", marker);
        assertThat(childTexts(stories, firstStory, "questions", "questionText")).containsExactly("E1", "E2");
        assertThat(childTexts(stories, secondStory, "questions", "questionText")).containsExactly("F1");
    }

    @Test
    void weakAreasListLessonsByTheirMostRecentWrongAnswer() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("b6b-weak-teacher"));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long alphaLesson = api.publishedLesson(teacherToken, "Weak Alpha " + suffix, "Alpha content");
        Long betaLesson = api.publishedLesson(teacherToken, "Weak Beta " + suffix, "Beta content");
        Long alphaQuiz = api.publishedQuiz(teacherToken, alphaLesson, null, null);
        Long betaQuiz = api.publishedQuiz(teacherToken, betaLesson, null, null);
        String studentEmail = LearningApiTestClient.uniqueEmail("b6b-weak-student");
        String studentToken = api.registerStudent(studentEmail);

        Long alphaFirst = submitWrongAttempt(studentToken, alphaQuiz);
        Long betaOnly = submitWrongAttempt(studentToken, betaQuiz);
        Long alphaLatest = submitWrongAttempt(studentToken, alphaQuiz);
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        setAnswerTimes(alphaFirst, base);
        setAnswerTimes(betaOnly, base.plusHours(1));
        setAnswerTimes(alphaLatest, base.plusHours(2));

        // Alpha's latest wrong answer is newer than Beta's, although its first one is older.
        JsonNode progress = api.data(mockMvc.perform(api.authGet("/api/v1/students/progress", studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(texts(progress.path("weakAreas"))).containsExactly("Weak Alpha " + suffix, "Weak Beta " + suffix);

        // The wrong-answer list resolves the correct option from the batch-loaded options.
        JsonNode wrongAnswers = api.data(mockMvc.perform(api.authGet("/api/v1/students/wrong-answers", studentToken))
                .andExpect(status().isOk())
                .andReturn());
        List<String> optionCorrectAnswers = new ArrayList<>();
        wrongAnswers.path("items").forEach(item -> {
            if ("MULTIPLE_CHOICE".equals(item.path("questionType").asText())) {
                optionCorrectAnswers.add(item.path("correctAnswer").asText());
            }
        });
        assertThat(optionCorrectAnswers).hasSize(3).containsOnly("你好");

        // Lesson mastery reads only the attempts of that lesson's quizzes.
        Long studentId = studentProfileRepository.findByUserId(
                userRepository.findByEmailWithRoles(studentEmail).orElseThrow().getId()).orElseThrow().getId();
        assertThat(quizAttemptRepository.findByStudentIdAndQuizLessonId(studentId, alphaLesson))
                .extracting(QuizAttempt::getId)
                .containsExactlyInAnyOrder(alphaFirst, alphaLatest);
        assertThat(quizQuestionRepository.countByQuizId(alphaQuiz)).isEqualTo(2);
    }

    @Test
    void teacherLiveClassesListUpcomingClassesFirstThenPastClassesNewestFirst() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("b6b-live-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Live ordering", "Speaking practice");
        LocalDateTime now = LocalDateTime.now();
        Long pastOld = createLiveClass(teacherToken, lessonId, "past old");
        Long upcomingLater = createLiveClass(teacherToken, lessonId, "upcoming later");
        Long pastRecent = createLiveClass(teacherToken, lessonId, "past recent");
        Long liveNow = createLiveClass(teacherToken, lessonId, "live now");
        Long upcomingSoon = createLiveClass(teacherToken, lessonId, "upcoming soon");
        reschedule(pastOld, now.minusDays(3), now.minusDays(3).plusHours(1));
        reschedule(upcomingLater, now.plusDays(2), now.plusDays(2).plusHours(1));
        reschedule(pastRecent, now.minusDays(1), now.minusDays(1).plusHours(1));
        reschedule(liveNow, now.minusMinutes(30), now.plusMinutes(30));
        reschedule(upcomingSoon, now.plusDays(1), now.plusDays(1).plusHours(1));

        JsonNode firstPage = teacherLiveClasses(teacherToken, 0, 3);
        assertThat(firstPage.path("totalItems").asLong()).isEqualTo(5);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(ids(firstPage)).containsExactly(liveNow, upcomingSoon, upcomingLater);
        assertThat(ids(teacherLiveClasses(teacherToken, 1, 3))).containsExactly(pastRecent, pastOld);
    }

    @Test
    void teacherQuizAttemptsArePagedNewestFirstWithTheFullTotal() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("b6b-attempt-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Attempt paging", "Attempt content");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);
        String studentToken = api.registerStudent(LearningApiTestClient.uniqueEmail("b6b-attempt-student"));
        Long oldest = submitWrongAttempt(studentToken, quizId);
        Long newest = submitWrongAttempt(studentToken, quizId);
        Long middle = submitWrongAttempt(studentToken, quizId);
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        jdbcTemplate.update("update quiz_attempts set completed_at = ? where id = ?", base, oldest);
        jdbcTemplate.update("update quiz_attempts set completed_at = ? where id = ?", base.plusHours(2), newest);
        jdbcTemplate.update("update quiz_attempts set completed_at = ? where id = ?", base.plusHours(1), middle);

        JsonNode firstPage = quizAttempts(teacherToken, quizId, 0, 2);
        assertThat(firstPage.path("totalItems").asLong()).isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(attemptIds(firstPage)).containsExactly(newest, middle);
        assertThat(attemptIds(quizAttempts(teacherToken, quizId, 1, 2))).containsExactly(oldest);
        JsonNode beyondLastPage = quizAttempts(teacherToken, quizId, 5, 2);
        assertThat(beyondLastPage.path("items")).isEmpty();
        assertThat(beyondLastPage.path("totalItems").asLong()).isEqualTo(3);
    }

    // ----- helpers -----

    private Long createActivity(String adminToken, String path, Map<String, Object> body) throws Exception {
        return api.id(mockMvc.perform(api.jsonPost("/api/v1/admin/activities/" + path, adminToken, body))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode listActivities(String adminToken, String path, String keyword) throws Exception {
        return api.data(mockMvc.perform(api.authGet("/api/v1/admin/activities/" + path, adminToken)
                        .param("keyword", keyword)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static Map<String, Object> storyQuestion(String text, int sortOrder) {
        return Map.of(
                "questionText", text,
                "optionsPayload", "[\"yes\",\"no\"]",
                "sortOrder", sortOrder,
                "correctOptionIndex", 0);
    }

    private static List<String> childTexts(JsonNode page, Long parentId, String childField, String textField) {
        for (JsonNode parent : page.path("items")) {
            if (parent.path("id").asLong() == parentId) {
                List<String> texts = new ArrayList<>();
                parent.path(childField).forEach(child -> texts.add(child.path(textField).asText()));
                return texts;
            }
        }
        throw new AssertionError("Parent " + parentId + " is missing from the list page.");
    }

    private Long submitWrongAttempt(String studentToken, Long quizId) throws Exception {
        return api.data(api.submitQuiz(studentToken, quizId, false, "wrong")).path("attemptId").asLong();
    }

    private void setAnswerTimes(Long attemptId, LocalDateTime createdAt) {
        jdbcTemplate.update("update quiz_attempt_answers set created_at = ? where quiz_attempt_id = ?", createdAt, attemptId);
    }

    private Long createLiveClass(String teacherToken, Long lessonId, String title) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(7);
        return api.id(mockMvc.perform(api.jsonPost("/api/v1/teachers/live-classes", teacherToken, Map.of(
                        "title", title,
                        "description", "Ordering check",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", start.plusHours(1).toString(),
                        "meetingLink", "https://meet.google.com/ordering-check",
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED",
                        "maxParticipants", 5)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void reschedule(Long liveClassId, LocalDateTime start, LocalDateTime end) {
        jdbcTemplate.update(
                "update live_classes set scheduled_start_at = ?, scheduled_end_at = ? where id = ?",
                start, end, liveClassId);
    }

    private JsonNode teacherLiveClasses(String teacherToken, int page, int size) throws Exception {
        return api.data(mockMvc.perform(api.authGet("/api/v1/teachers/live-classes", teacherToken)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode quizAttempts(String teacherToken, Long quizId, int page, int size) throws Exception {
        return api.data(mockMvc.perform(api.authGet("/api/v1/teachers/quizzes/" + quizId + "/attempts", teacherToken)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private static List<Long> attemptIds(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("attemptId").asLong()));
        return ids;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(item -> texts.add(item.asText()));
        return texts;
    }
}
