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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Small MockMvc client shared by the integration tests: accounts, approved teachers, classes with published
 * lessons and quizzes (through the teacher-submit / admin-approve review), joining and taking quizzes, live
 * sessions, the Ask SparkBot stream and JSON helpers. Requires the test context properties DEFAULT_ADMIN_EMAIL /
 * DEFAULT_ADMIN_PASSWORD below.
 */
public final class LearningApiTestClient {

    public static final String ADMIN_EMAIL = "admin@example.com";
    public static final String ADMIN_PASSWORD = "SuperSecure123!";
    public static final String USER_PASSWORD = "Password123!";
    /** The largest page the list endpoints return, whatever size is requested. */
    public static final int MAX_PAGE_SIZE = 100;
    public static final String AI_CHAT_STREAM_PATH = "/api/v1/ai/chat/stream";
    private static final String CHAT_COMPLETE_EVENT = "event:complete\ndata:";
    private static final long CHAT_STREAM_WAIT_MS = 5_000;
    private static final long BEGINNER_LEVEL_ID = 1L;
    private static final long TOPIC_ID = 1L;
    private static final String OVERSIZED_PAGE_SIZE = "100000";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    /** Classes of the lessons and quizzes this client published, so a student can join before taking a quiz. */
    private final Map<Long, Long> classOfLesson = new HashMap<>();
    private final Map<Long, Long> classOfQuiz = new HashMap<>();

    public LearningApiTestClient(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            TeacherProfileRepository teacherProfileRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
    }

    // ----- accounts -----

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    /** Eight random characters, to keep titles and search markers of one test apart from the others. */
    public static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Registers a student named "Student {email}" and signs in. */
    public String registerStudent(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/student", null, Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());
        return login(email, USER_PASSWORD);
    }

    /** Registers a (pending) teacher named "Teacher {email}" and signs in. */
    public String registerTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/teacher", null, Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Mandarin teacher for young adult learners")))
                .andExpect(status().isOk());
        return login(email, USER_PASSWORD);
    }

    /**
     * Registers a teacher, uploads the required documents (teaching certificate and identity document) and submits
     * the application; the teacher stays PENDING, in review.
     */
    public TeacherProfile registerTeacherWithDocument(String email) throws Exception {
        String token = registerTeacher(email);
        uploadVerificationDocument(token, "CERTIFICATE");
        uploadVerificationDocument(token, "IDENTITY");
        mockMvc.perform(jsonPost("/api/v1/teachers/verification/submit", token, Map.of("acceptedStandards", true)))
                .andExpect(status().isOk());
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    /** Uploads one verification document (a minimal PDF) of the given type for the signed-in teacher. */
    public void uploadVerificationDocument(String teacherToken, String documentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", documentType + ".pdf", "application/pdf", TestUploadFixtures.minimalPdf());
        mockMvc.perform(authMultipart("/api/v1/teachers/verification-documents", teacherToken)
                        .file(file)
                        .param("documentType", documentType))
                .andExpect(status().isOk());
    }

    /** Registers a teacher with a verification document and lets the admin approve it. */
    private TeacherProfile approveTeacher(String email) throws Exception {
        TeacherProfile profile = registerTeacherWithDocument(email);
        mockMvc.perform(jsonPost("/api/v1/admin/teachers/" + profile.getId() + "/approve", adminToken(),
                        Map.of("note", "Approved")))
                .andExpect(status().isOk());
        assertThat(teacherProfileRepository.findById(profile.getId()).orElseThrow().getTeacherStatus())
                .isEqualTo(TeacherStatus.APPROVED);
        return profile;
    }

    /** {@link #approveTeacher(String)}, then signs the approved teacher in. */
    public String approvedTeacher(String email) throws Exception {
        approveTeacher(email);
        return login(email, USER_PASSWORD);
    }

    public String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    public String login(String email, String password) throws Exception {
        return data(mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk())
                .andReturn()).path("token").asText();
    }

    public MockHttpServletRequestBuilder loginRequest(String email, String password) throws Exception {
        return jsonPost("/api/v1/auth/login", null, Map.of("email", email, "password", password));
    }

    // ----- classes (teacher authoring, admin review, student access), exactly as in production -----

    /** Ids of a published class with one unit, one lesson and the lesson's quiz. */
    public record PublishedClass(Long classId, Long unitId, Long lessonId, Long quizId) {
    }

    /** A draft class of the teacher (Beginner 1, the first topic, hybrid). */
    public Long draftClass(String teacherToken, String title) throws Exception {
        return data(mockMvc.perform(jsonPost("/api/v1/teachers/classes", teacherToken, Map.of(
                        "title", title,
                        "description", "A class for adult beginners",
                        "levelId", BEGINNER_LEVEL_ID,
                        "topicId", TOPIC_ID,
                        "learningType", "HYBRID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()).path("id").asLong();
    }

    /** Adds a unit to the class and returns its id. */
    public Long unit(String teacherToken, Long classId, String title) throws Exception {
        JsonNode units = data(mockMvc.perform(jsonPost("/api/v1/teachers/classes/" + classId + "/units", teacherToken,
                        Map.of("title", title)))
                .andExpect(status().isOk())
                .andReturn()).path("units");
        return units.get(units.size() - 1).path("id").asLong();
    }

    /** A draft lesson in the unit; returns its id. */
    public Long draftLesson(String teacherToken, Long unitId, String title) throws Exception {
        return data(mockMvc.perform(jsonPost("/api/v1/teachers/units/" + unitId + "/lessons", teacherToken,
                        Map.of("title", title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()).path("lessonId").asLong();
    }

    /**
     * Fills a lesson so it passes the submit checklist: a key phrase, a flashcards activity and a 3-question quiz
     * (each question 5 marks). Returns the editor.
     */
    public JsonNode readyLesson(String teacherToken, Long lessonId, String summary) throws Exception {
        String base = "/api/v1/teachers/lessons/" + lessonId;
        JsonNode editor = data(mockMvc.perform(jsonPut(base + "/content", teacherToken, Map.of(
                        "title", data(mockMvc.perform(authGet(base, teacherToken)).andReturn()).path("title").asText(),
                        "minutes", 12,
                        "summary", summary,
                        "keyPhrases", List.of(Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello")))))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(jsonPost(base + "/activities", teacherToken, Map.of(
                        "type", "FLASHCARDS", "title", "Greeting cards", "stage", "GUIDED")))
                .andExpect(status().isOk());
        editor = data(mockMvc.perform(jsonPost(base + "/quizzes", teacherToken, Map.of(
                        "title", "Lesson quiz", "timeLimitMinutes", 10, "learningStage", "GUIDED")))
                .andExpect(status().isOk())
                .andReturn());
        long quizId = editor.path("quizzes").get(editor.path("quizzes").size() - 1).path("id").asLong();
        for (Map<String, ?> question : List.of(optionQuestion("Which word means hello?", "你好", "再见", null),
                fillBlankQuestion(), optionQuestion("Which word means goodbye?", "再见", "你好", null))) {
            editor = data(mockMvc.perform(jsonPost(base + "/quizzes/" + quizId + "/questions", teacherToken, question))
                    .andExpect(status().isOk())
                    .andReturn());
        }
        return editor;
    }

    /** A published class whose one lesson passed review (the class goes live with it). */
    public PublishedClass publishedClass(String teacherToken, String title) throws Exception {
        return publishedClass(teacherToken, title, title + " lesson", "Lesson summary");
    }

    public PublishedClass publishedClass(String teacherToken, String classTitle, String lessonTitle, String summary)
            throws Exception {
        Long classId = draftClass(teacherToken, classTitle);
        Long unitId = unit(teacherToken, classId, "Unit 1");
        Long lessonId = draftLesson(teacherToken, unitId, lessonTitle);
        JsonNode editor = readyLesson(teacherToken, lessonId, summary);
        mockMvc.perform(authPost("/api/v1/teachers/lessons/" + lessonId + "/submit", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mockMvc.perform(authPost("/api/v1/admin/class-reviews/classes/" + classId + "/approve", adminToken()))
                .andExpect(status().isOk());
        Long quizId = editor.path("quizzes").get(0).path("id").asLong();
        classOfLesson.put(lessonId, classId);
        classOfQuiz.put(quizId, classId);
        return new PublishedClass(classId, unitId, lessonId, quizId);
    }

    /** A published lesson (in a class of its own); {@code content} becomes its summary. */
    public Long publishedLesson(String teacherToken, String title, String content) throws Exception {
        return publishedClass(teacherToken, "Class: " + title, title, content).lessonId();
    }

    /**
     * Adds a quiz to a published lesson through a reviewed revision: an option question (5 marks, the optional
     * hint), a fill-blank question answered "hǎo" (5 marks) and an option question (5 marks).
     */
    public Long publishedQuiz(String teacherToken, Long lessonId, String learningStage, String hint) throws Exception {
        String base = "/api/v1/teachers/lessons/" + lessonId;
        Map<String, Object> quiz = new HashMap<>(Map.of("title", "Greeting quiz", "description", "Greetings practice"));
        if (learningStage != null) {
            quiz.put("learningStage", learningStage);
        }
        JsonNode editor = data(mockMvc.perform(jsonPost(base + "/quizzes", teacherToken, quiz))
                .andExpect(status().isOk())
                .andReturn());
        long quizId = editor.path("quizzes").get(editor.path("quizzes").size() - 1).path("id").asLong();
        for (Map<String, ?> question : List.of(optionQuestion("Which word means hello?", "你好", "再见", hint),
                fillBlankQuestion(), optionQuestion("Which word means goodbye?", "再见", "你好", null))) {
            mockMvc.perform(jsonPost(base + "/quizzes/" + quizId + "/questions", teacherToken, question))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(authPost(base + "/submit", teacherToken)).andExpect(status().isOk());
        mockMvc.perform(authPost("/api/v1/admin/class-reviews/lessons/" + lessonId + "/approve", adminToken()))
                .andExpect(status().isOk());
        classOfQuiz.put(quizId, classOfLesson.get(lessonId));
        return quizId;
    }

    private static Map<String, Object> optionQuestion(String text, String correct, String wrong, String hint) {
        Map<String, Object> question = new HashMap<>(Map.of(
                "questionType", "MULTIPLE_CHOICE",
                "questionText", text,
                "points", 5,
                "explanation", correct + " is the answer.",
                "options", List.of(
                        Map.of("optionText", correct, "correctOption", true, "displayOrder", 1),
                        Map.of("optionText", wrong, "correctOption", false, "displayOrder", 2))));
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

    /** The student joins the class (joining twice is harmless). */
    public void join(String studentToken, Long classId) throws Exception {
        mockMvc.perform(authPost("/api/v1/students/classes/" + classId + "/join", studentToken))
                .andExpect(status().isOk());
    }

    /** Starts (or resumes) the student's attempt at the quiz; the student joins its class first. */
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

    /**
     * Takes the quiz of {@link #readyLesson} or {@link #publishedQuiz}: the first option question right or wrong, the
     * fill-blank answer as given and the last option question right; then submits.
     */
    public MvcResult submitQuiz(String studentToken, Long quizId, boolean optionCorrect, String fillBlank) throws Exception {
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
        return mockMvc.perform(authPost("/api/v1/students/classes/quiz-attempts/" + attemptId + "/submit", studentToken))
                .andExpect(status().isOk())
                .andReturn();
    }

    // ----- live sessions -----

    /** A live-session form for 45 minutes from {@code start} (wall-clock time in the session zone) on Google Meet. */
    public static Map<String, Object> liveSessionBody(Long classId, Long lessonId, String title, LocalDateTime start) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "title", title,
                "classId", classId,
                "startTime", start.toString(),
                "endTime", start.plusMinutes(45).toString(),
                "meetingLink", "https://meet.google.com/" + UUID.randomUUID(),
                "meetingPlatform", "GOOGLE_MEET"));
        if (lessonId != null) {
            body.put("lessonId", lessonId);
        }
        return body;
    }

    /** Schedules a session of the lesson's class (see {@link #publishedLesson}) for 45 minutes from {@code start}. */
    public Long scheduledLiveClass(String teacherToken, Long lessonId, String title, LocalDateTime start) throws Exception {
        return id(mockMvc.perform(jsonPost("/api/v1/teachers/live-sessions", teacherToken,
                        liveSessionBody(classOfLesson.get(lessonId), lessonId, title, start)))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** A student action on a live session: {@code book}, {@code cancel-booking} or {@code join}. */
    public MockHttpServletRequestBuilder liveSessionAction(String studentToken, Long sessionId, String action) {
        return authPost("/api/v1/students/classes/live-sessions/" + sessionId + "/" + action, studentToken);
    }

    /** The class a lesson created by {@link #publishedLesson} belongs to. */
    public Long classOfLesson(Long lessonId) {
        return classOfLesson.get(lessonId);
    }

    // ----- AI Helper -----

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
        int event = stream.indexOf(CHAT_COMPLETE_EVENT);
        assertThat(event).as("complete event in %s", stream).isNotNegative();
        int start = event + CHAT_COMPLETE_EVENT.length();
        return objectMapper.readTree(stream.substring(start, stream.indexOf('\n', start)));
    }

    // ----- HTTP helpers -----

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
        return authMultipart(HttpMethod.POST, path, token);
    }

    public MockMultipartHttpServletRequestBuilder authMultipart(HttpMethod method, String path, String token) {
        MockMultipartHttpServletRequestBuilder builder = multipart(method, path);
        builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return builder;
    }

    public MockHttpServletRequestBuilder jsonPost(String path, String token, Object body) throws Exception {
        return json(post(path), token, body);
    }

    public MockHttpServletRequestBuilder jsonPut(String path, String token, Object body) throws Exception {
        return json(put(path), token, body);
    }

    public MockHttpServletRequestBuilder jsonPatch(String path, String token, Object body) throws Exception {
        return json(patch(path), token, body);
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    public Long id(MvcResult result) throws Exception {
        return data(result).path("id").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
