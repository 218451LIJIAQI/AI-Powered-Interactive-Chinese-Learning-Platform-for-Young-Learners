package com.example.aichinese.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Small MockMvc client shared by the integration tests: accounts, classes with published lessons and quizzes (made
 * through the admin's class authoring), joining and taking quizzes, the Ask SparkBot stream and JSON helpers. Requires
 * the test context properties DEFAULT_ADMIN_EMAIL / DEFAULT_ADMIN_PASSWORD below.
 */
public final class LearningApiTestClient {

    public static final String ADMIN_EMAIL = "admin@example.com";
    public static final String ADMIN_PASSWORD = "SuperSecure123!";
    public static final String USER_PASSWORD = "Password123!";
    /** The largest page the list endpoints return, whatever size is requested. */
    public static final int MAX_PAGE_SIZE = 100;
    public static final String PLAN = "/api/v1/students/plan";
    public static final Map<String, Object> SKIPPED_CHECK = Map.of("selfLevel", "NEW_TO_MANDARIN", "skipped", true);
    public static final String AI_CHAT_STREAM_PATH = "/api/v1/ai/chat/stream";
    private static final String CHAT_COMPLETE_EVENT = "event:complete\ndata:";
    private static final long CHAT_STREAM_WAIT_MS = 5_000;
    private static final long CATEGORY_ID = 1L;
    private static final String OVERSIZED_PAGE_SIZE = "100000";
    /** Thread-safe allocator over RFC 2544 benchmarking block 198.18.0.0/15 so shared rate-limit buckets never collide. */
    private static final AtomicInteger NEXT_CLIENT_IP = new AtomicInteger(1);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    /** Classes of the quizzes this client published, so a learner can join before taking a quiz. */
    private final Map<Long, Long> classOfQuiz = new HashMap<>();

    public LearningApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // Accounts

    /** Keep the full random suffix and fit the test label into the 64-character email local part. */
    public static String uniqueEmail(String label) {
        String suffix = "-" + UUID.randomUUID();
        int labelLength = Math.min(label.length(), 64 - suffix.length());
        return label.substring(0, labelLength) + suffix + "@example.com";
    }

    /** Eight random characters, to keep titles and search markers of one test apart from the others. */
    public static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A unique non-loopback IPv4 client address (198.18.x.y with y in 1..254) for isolated rate-limit buckets. */
    public static String nextClientIp() {
        int sequence = NEXT_CLIENT_IP.getAndIncrement();
        int thirdOctet = (sequence / 254) % 256;
        int fourthOctet = (sequence % 254) + 1;
        return "198.18." + thirdOctet + "." + fourthOctet;
    }

    /** Registers a learner named "Student {email}" and signs in. */
    public String registerStudent(String email) throws Exception {
        registerStudentAccount(email);
        return login(email, USER_PASSWORD);
    }

    /** Registers a learner without creating a sign-in session. */
    public void registerStudentAccount(String email) throws Exception {
        mockMvc.perform(studentRegistrationRequest(email, USER_PASSWORD))
                .andExpect(status().isOk());
    }

    public MockHttpServletRequestBuilder studentRegistrationRequest(String email, String password) throws Exception {
        return jsonPost("/api/v1/auth/register/student", null, Map.of(
                "fullName", "Student " + email,
                "email", email,
                "password", password,
                "confirmPassword", password,
                "preferredLanguage", "en"));
    }

    public String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    public JsonNode startTravelPlan(String token) throws Exception {
        return startPlan(token, Map.of("reason", "TRAVEL", "goals", List.of("ORDER_FOOD"),
                "pace", "HOURS_1_TO_2", "focus", List.of()), SKIPPED_CHECK);
    }

    /** Build the plan, then confirm it through the same endpoints as the learner. */
    public JsonNode startPlan(String token, Map<String, Object> goal, Map<String, Object> levelCheck) throws Exception {
        mockMvc.perform(jsonPost(PLAN, token, Map.of("goal", goal, "levelCheck", levelCheck)))
                .andExpect(status().isOk());
        return data(mockMvc.perform(authPost(PLAN + "/confirm", token)).andExpect(status().isOk()).andReturn());
    }

    public String login(String email, String password) throws Exception {
        return data(mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk())
                .andReturn()).path("token").asText();
    }

    public MockHttpServletRequestBuilder loginRequest(String email, String password) throws Exception {
        return jsonPost("/api/v1/auth/login", null, Map.of("email", email, "password", password));
    }

    public MockHttpServletRequestBuilder changePasswordRequest(
            String token, String currentPassword, String newPassword, String confirmPassword) throws Exception {
        return jsonPost("/api/v1/users/me/password", token, Map.of(
                "currentPassword", currentPassword,
                "newPassword", newPassword,
                "confirmPassword", confirmPassword));
    }

    // Classes: admin authoring and learner access, through the production endpoints

    /** Ids of a published class (2 units of 2 published lessons) with its first unit, lesson and lesson quiz. */
    public record PublishedClass(Long classId, Long unitId, Long lessonId, Long quizId) {
    }

    /** A draft class in the first category. */
    public Long draftClass(String adminToken, String title) throws Exception {
        return data(mockMvc.perform(jsonPost("/api/v1/admin/classes", adminToken, Map.of(
                        "title", title,
                        "description", "A class for adult beginners",
                        "categoryId", CATEGORY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()).path("id").asLong();
    }

    /** Adds a unit to the class and returns its id. */
    public Long unit(String adminToken, Long classId, String title) throws Exception {
        JsonNode units = data(mockMvc.perform(jsonPost("/api/v1/admin/classes/" + classId + "/units", adminToken,
                        Map.of("title", title)))
                .andExpect(status().isOk())
                .andReturn()).path("units");
        return units.get(units.size() - 1).path("id").asLong();
    }

    /** A draft lesson in the unit; returns its id. */
    public Long draftLesson(String adminToken, Long unitId, String title) throws Exception {
        return createLesson(adminToken, unitId, title).path("lessonId").asLong();
    }

    private JsonNode createLesson(String adminToken, Long unitId, String title) throws Exception {
        return data(mockMvc.perform(jsonPost("/api/v1/admin/units/" + unitId + "/lessons", adminToken,
                        Map.of("title", title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn());
    }

    /**
     * Fills a lesson so it can be published: a key phrase, a video, its three activities (flashcards, tracing 你,
     * matching two greetings) and a 5-question quiz (each question 5 marks: "hello", a fill-in, "goodbye", "thank you"
     * and "sorry") with "Allow SparkBot hints" on. Returns the editor.
     */
    public JsonNode readyLesson(String adminToken, Long lessonId) throws Exception {
        String title = data(mockMvc.perform(authGet("/api/v1/admin/lessons/" + lessonId, adminToken))
                .andExpect(status().isOk()).andReturn()).path("title").asText();
        return fillLesson(adminToken, lessonId, title, true, null);
    }

    private JsonNode fillLesson(String adminToken, Long lessonId, String title, boolean hintsAllowed,
            String hint) throws Exception {
        String base = "/api/v1/admin/lessons/" + lessonId;
        mockMvc.perform(jsonPut(base + "/content", adminToken, Map.of(
                        "title", title,
                        "keyPhrases", List.of(Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello")))))
                .andExpect(status().isOk());
        uploadLessonVideo(adminToken, lessonId, "Saying hello");
        mockMvc.perform(jsonPost(base + "/activities", adminToken, Map.of(
                        "type", "FLASHCARDS", "title", "Greeting cards")))
                .andExpect(status().isOk());
        mockMvc.perform(jsonPost(base + "/activities", adminToken, Map.of(
                        "type", "TRACING", "title", "Trace 你",
                        "characters", List.of(Map.of("character", "你", "pinyin", "nǐ", "meaning", "you")))))
                .andExpect(status().isOk());
        mockMvc.perform(jsonPost(base + "/activities", adminToken, Map.of(
                        "type", "MATCHING", "title", "Match the greetings",
                        "pairs", List.of(
                                Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello"),
                                Map.of("chinese", "再见", "pinyin", "zàijiàn", "meaning", "goodbye")))))
                .andExpect(status().isOk());
        JsonNode editor = data(mockMvc.perform(jsonPost(base + "/quizzes", adminToken,
                        Map.of("title", "Lesson quiz", "hintsAllowed", hintsAllowed)))
                .andExpect(status().isOk())
                .andReturn());
        long quizId = editor.path("quizzes").get(0).path("id").asLong();
        for (Map<String, ?> question : quizQuestions(hint)) {
            editor = data(mockMvc.perform(jsonPost(base + "/quizzes/" + quizId + "/questions", adminToken, question))
                    .andExpect(status().isOk())
                    .andReturn());
        }
        return editor;
    }

    /** The five questions of {@link #readyLesson}'s quiz, in order; the wrong answer of the first has feedback. */
    public static List<Map<String, ?>> quizQuestions() {
        return quizQuestions(null);
    }

    private static List<Map<String, ?>> quizQuestions(String hint) {
        return List.of(
                optionQuestion("Which word means hello?", "你好", "再见", hint, "That's goodbye."),
                fillBlankQuestion(),
                optionQuestion("Which word means goodbye?", "再见", "你好", null, null),
                optionQuestion("Which word means thank you?", "谢谢", "再见", null, null),
                optionQuestion("Which word means sorry?", "对不起", "谢谢", null, null));
    }

    /** Uploads a lesson video (a minimal MP4) to the lesson's working copy. */
    public void uploadLessonVideo(String adminToken, Long lessonId, String title) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", TestUploadFixtures.minimalMp4());
        mockMvc.perform(authMultipart("/api/v1/admin/lessons/" + lessonId + "/video", adminToken)
                        .file(file)
                        .param("title", title)
                        .param("durationSeconds", "280"))
                .andExpect(status().isOk());
    }

    /** Publishes the lesson (a draft, or the unpublished changes of a published lesson); returns the editor. */
    public JsonNode publishLesson(String adminToken, Long lessonId) throws Exception {
        return data(mockMvc.perform(authPost("/api/v1/admin/lessons/" + lessonId + "/publish", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andReturn());
    }

    /** A complete lesson in the unit, published with hints allowed; returns the editor. */
    public JsonNode publishedLesson(String adminToken, Long unitId, String title) throws Exception {
        return publishedLesson(adminToken, unitId, title, true, null);
    }

    private JsonNode publishedLesson(String adminToken, Long unitId, String title, boolean hintsAllowed,
            String hint) throws Exception {
        JsonNode draft = createLesson(adminToken, unitId, title);
        long lessonId = draft.path("lessonId").asLong();
        fillLesson(adminToken, lessonId, draft.path("title").asText(), hintsAllowed, hint);
        return publishLesson(adminToken, lessonId);
    }

    /** A published class: 2 units of 2 published lessons (the shape a class needs to go live). */
    public PublishedClass publishedClass(String adminToken, String title) throws Exception {
        return publishedClass(adminToken, title, title + " lesson");
    }

    public PublishedClass publishedClass(String adminToken, String classTitle, String lessonTitle)
            throws Exception {
        return publishedClass(adminToken, classTitle, lessonTitle, true, null);
    }

    /**
     * A published class whose first lesson's quiz has "Allow SparkBot hints" as given and the optional {@code hint} on
     * its first question; the quizzes are otherwise those of {@link #readyLesson}.
     */
    public PublishedClass publishedClassWithQuiz(String adminToken, String title, boolean hintsAllowed, String hint)
            throws Exception {
        return publishedClass(adminToken, title, title + " lesson", hintsAllowed, hint);
    }

    private PublishedClass publishedClass(
            String adminToken, String classTitle, String lessonTitle, boolean hintsAllowed, String hint)
            throws Exception {
        Long classId = draftClass(adminToken, classTitle);
        Long unitId = unit(adminToken, classId, "Unit 1");
        Long secondUnitId = unit(adminToken, classId, "Unit 2");
        JsonNode first = publishedLesson(adminToken, unitId, lessonTitle, hintsAllowed, hint);
        publishedLesson(adminToken, unitId, lessonTitle + " 2");
        publishedLesson(adminToken, secondUnitId, lessonTitle + " 3");
        publishedLesson(adminToken, secondUnitId, lessonTitle + " 4");
        publishClass(adminToken, classId);
        Long quizId = first.path("quizzes").get(0).path("id").asLong();
        classOfQuiz.put(quizId, classId);
        return new PublishedClass(classId, unitId, first.path("lessonId").asLong(), quizId);
    }

    /** Publishes the class (it must have its shape: 2 units holding 4 to 6 published lessons). */
    private void publishClass(String adminToken, Long classId) throws Exception {
        mockMvc.perform(authPost("/api/v1/admin/classes/" + classId + "/publish", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private static Map<String, Object> optionQuestion(String text, String correct, String wrong, String hint,
            String wrongFeedback) {
        Map<String, Object> wrongOption = new HashMap<>(Map.of("optionText", wrong, "correctOption", false));
        if (wrongFeedback != null) {
            wrongOption.put("feedback", wrongFeedback);
        }
        Map<String, Object> question = new HashMap<>(Map.of(
                "questionType", "MULTIPLE_CHOICE",
                "questionText", text,
                "points", 5,
                "explanation", correct + " is the answer.",
                "options", List.of(
                        Map.of("optionText", correct, "correctOption", true),
                        wrongOption)));
        if (hint != null) {
            question.put("hint", hint);
        }
        return question;
    }

    private static Map<String, Object> fillBlankQuestion() {
        return Map.of(
                "questionType", "FILL_BLANK",
                "questionText", "Complete: nǐ ____ (hello)",
                "correctAnswerText", "hǎo",
                "points", 5,
                "explanation", "nǐ hǎo");
    }

    /** The learner joins the class (joining twice is harmless). */
    public void join(String studentToken, Long classId) throws Exception {
        mockMvc.perform(authPost("/api/v1/students/classes/" + classId + "/join", studentToken))
                .andExpect(status().isOk());
    }

    /** Starts (or resumes) the learner's attempt at the quiz; the learner joins its class first. */
    public JsonNode startQuiz(String studentToken, Long quizId) throws Exception {
        if (classOfQuiz.containsKey(quizId)) {
            join(studentToken, classOfQuiz.get(quizId));
        }
        return data(mockMvc.perform(authPost("/api/v1/students/classes/quizzes/" + quizId + "/attempts", studentToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Saves one answer of the attempt: an option id or a typed answer. */
    public MockHttpServletRequestBuilder saveAnswer(String studentToken, long attemptId, Map<String, ?> answer) throws Exception {
        return jsonPut("/api/v1/students/classes/quiz-attempts/" + attemptId + "/answers", studentToken, answer);
    }

    /** Takes the quiz of {@link #readyLesson} as {@link #answerQuiz} answers it, then submits. */
    public MvcResult submitQuiz(String studentToken, Long quizId, boolean optionCorrect, String fillBlank) throws Exception {
        long attemptId = answerQuiz(studentToken, quizId, optionCorrect, fillBlank);
        return mockMvc.perform(authPost("/api/v1/students/classes/quiz-attempts/" + attemptId + "/submit", studentToken))
                .andExpect(status().isOk())
                .andReturn();
    }

    /**
     * Answers the quiz of {@link #readyLesson} without submitting it: the first option question right or wrong, the
     * fill-in answer as given and the other option questions right. Returns the attempt's id.
     */
    public long answerQuiz(String studentToken, Long quizId, boolean optionCorrect, String fillBlank) throws Exception {
        JsonNode session = startQuiz(studentToken, quizId);
        long attemptId = session.path("attemptId").asLong();
        JsonNode questions = session.path("questions");
        for (int index = 0; index < questions.size(); index++) {
            JsonNode question = questions.get(index);
            Map<String, Object> answer = new HashMap<>(Map.of("questionId", question.path("id").asLong()));
            if (question.path("type").asText().equals("FILL_BLANK")) {
                answer.put("answerText", fillBlank);
            } else {
                boolean first = index == 0;
                answer.put("optionId", question.path("options").get(first && !optionCorrect ? 1 : 0).path("id").asLong());
            }
            mockMvc.perform(saveAnswer(studentToken, attemptId, answer)).andExpect(status().isOk());
        }
        return attemptId;
    }

    // AI Helper

    /**
     * Asks SparkBot through {@link #AI_CHAT_STREAM_PATH} and returns the whole event stream once it has ended: the
     * answer as {@code delta} events, then a {@code complete} or an {@code error} event.
     */
    public String chatStream(String studentToken, Map<String, ?> body) throws Exception {
        MvcResult pending = mockMvc.perform(jsonPost(AI_CHAT_STREAM_PATH, studentToken, body)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.getAsyncResult(CHAT_STREAM_WAIT_MS);
        return mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** The chat turn sent as the {@code complete} event of {@link #chatStream}; fails when the stream ended otherwise. */
    public JsonNode chat(String studentToken, Map<String, ?> body) throws Exception {
        String stream = chatStream(studentToken, body);
        int event = stream.lastIndexOf(CHAT_COMPLETE_EVENT);
        assertThat(event).as("complete event in %s", stream).isNotNegative();
        int start = event + CHAT_COMPLETE_EVENT.length();
        int end = stream.indexOf('\n', start);
        String payload = (end < 0 ? stream.substring(start) : stream.substring(start, end)).stripTrailing();
        return objectMapper.readTree(payload);
    }

    // HTTP helpers

    public MockHttpServletRequestBuilder authGet(String path, String token) {
        return get(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    /** A list request for a page far larger than {@link #MAX_PAGE_SIZE}. */
    public MockHttpServletRequestBuilder oversizedPageGet(String path, String token) {
        return authGet(path, token).param("size", OVERSIZED_PAGE_SIZE);
    }

    public MockHttpServletRequestBuilder authPost(String path, String token) {
        return post(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder authPatch(String path, String token) {
        return patch(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder authDelete(String path, String token) {
        return delete(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    /** A multipart upload of the signed-in user; files and form fields are added by the caller. */
    public MockMultipartHttpServletRequestBuilder authMultipart(String path, String token) {
        MockMultipartHttpServletRequestBuilder builder = multipart(path);
        builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return builder;
    }

    public MockHttpServletRequestBuilder jsonPost(String path, String token, Object body) throws Exception {
        return json(post(path), token, body);
    }

    public MockHttpServletRequestBuilder jsonPut(String path, String token, Object body) throws Exception {
        return json(put(path), token, body);
    }

    /** A null token sends no Authorization header (an anonymous request), never "Bearer null". */
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String token, Object body)
            throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        return token == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    /** Sends the request from the client address {@code ip} (the per-IP rate limits key on it). */
    public static RequestPostProcessor fromRemoteAddress(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** The response carries {@code Cache-Control: no-store}. */
    public static ResultMatcher noStore() {
        return header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"));
    }

    public JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    public Long id(MvcResult result) throws Exception {
        return data(result).path("id").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
