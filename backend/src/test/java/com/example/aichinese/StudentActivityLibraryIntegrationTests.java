package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage of the student-side activity library
 * ({@code /api/v1/students/activities/**}). Verifies:
 *
 * <ul>
 *   <li>Only PUBLISHED activities are surfaced to students.</li>
 *   <li>Completion awards the activity's {@code xpReward} exactly once via
 *       the {@code points_transactions} table.</li>
 *   <li>Filtering by HSK / topic / type narrows results.</li>
 *   <li>Role gates: admins, teachers and guests cannot use the student
 *       endpoints.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentActivityLibraryIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String STUDENT_ACTIVITIES_ENDPOINT = "/api/v1/students/activities";
    private static final String ADMIN_FLASHCARDS_ENDPOINT = "/api/v1/admin/activities/flashcards";
    private static final String ADMIN_MATCHING_GAMES_ENDPOINT = "/api/v1/admin/activities/matching-games";
    private static final String STUDENT_REGISTER_ENDPOINT = "/api/v1/auth/register/student";
    private static final String TEACHER_REGISTER_ENDPOINT = "/api/v1/auth/register/teacher";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String COMPLETE_SUFFIX = "/complete";
    private static final String STATUS_FIELD = "status";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String ARCHIVED = "ARCHIVED";
    private static final String FLASHCARD = "FLASHCARD";
    private static final String MATCHING_GAME = "MATCHING_GAME";
    private static final String PLATFORM_FLASHCARD = "PLATFORM_FLASHCARD";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String EMAIL_DOMAIN = "@example.com";
    private static final String TEACHER_BIO = "Teacher bio";
    private static final String TOPIC_PARAM = "topic";
    private static final String TYPE_PARAM = "type";
    private static final String KEYWORD_PARAM = "keyword";
    private static final String PAGE_PARAM = "page";
    private static final String SIZE_PARAM = "size";
    private static final String PAGE_SIZE_TEN = "10";
    private static final String DEEP_PAGE = "1000";
    private static final String PAGE_SIZE_ONE_HUNDRED = "100";
    private static final int DEFAULT_HSK_LEVEL = 1;
    private static final int DEFAULT_FLASHCARD_SECONDS = 30;
    private static final int DEFAULT_MATCHING_SECONDS = 60;
    private static final int DEFAULT_MATCHING_XP = 20;
    private static final int OVERSIZED_FILTER_LENGTH = 201;
    private static final LocalDateTime OLDER_ACTIVITY_TIME = LocalDateTime.of(2025, 1, 1, 9, 0);
    private static final LocalDateTime NEWER_ACTIVITY_TIME = LocalDateTime.of(2025, 1, 1, 9, 5);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void studentCanListAndCompletePublishedFlashcardAndXpIsAwardedExactlyOnce() throws Exception {
        String adminToken = adminToken();

        long publishedId = createPublishedFlashcard(adminToken,
                "Greetings 你好", "你好", "Hello", DEFAULT_HSK_LEVEL, "greetings", 15);
        long draftId = createFlashcard(adminToken,
                Map.of("title", "Draft Flashcard", "frontText", "再见", "backText", "Goodbye"));

        String studentEmail = uniqueEmail("activity-student");
        String studentToken = registerStudentAndLogin(studentEmail);

        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath(activityItemWithId(publishedId)).exists())
                // Draft must never leak through the student library feed.
                .andExpect(jsonPath(activityItemWithId(draftId)).doesNotExist())
                // The `completed` flag is exposed on every summary; concrete
                // value is exercised by the detail-call assertion further down.
                .andExpect(jsonPath(activityItemWithId(publishedId) + ".completed").exists());

        mockMvc.perform(authenticatedGet(studentFlashcardPath(publishedId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frontText").value("你好"))
                .andExpect(jsonPath("$.data.xpReward").value(15))
                .andExpect(jsonPath("$.data.completed").value(false));

        mockMvc.perform(authenticatedGet(studentFlashcardPath(draftId), studentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(authenticatedPost(studentFlashcardCompletionPath(publishedId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnedPoints").value(15))
                .andExpect(jsonPath("$.data.totalPoints").value(15))
                .andExpect(jsonPath("$.data.firstCompletion").value(true))
                .andExpect(jsonPath("$.data.type").value(FLASHCARD));

        mockMvc.perform(authenticatedPost(studentFlashcardCompletionPath(publishedId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnedPoints").value(0))
                .andExpect(jsonPath("$.data.totalPoints").value(15))
                .andExpect(jsonPath("$.data.firstCompletion").value(false));

        mockMvc.perform(authenticatedGet(studentFlashcardPath(publishedId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        StudentProfile student = findStudentProfile(studentEmail);
        assertThat(student.getTotalPoints()).isEqualTo(15);
        List<PointsTransaction> txs = pointsTransactionRepository
                .findByStudentIdOrderByCreatedAtDesc(student.getId());
        assertThat(txs)
                .filteredOn(tx -> PLATFORM_FLASHCARD.equals(tx.getSourceType()))
                .hasSize(1)
                .first()
                .satisfies(tx -> {
                    assertThat(tx.getPointsDelta()).isEqualTo(15);
                    assertThat(tx.getSourceId()).isEqualTo(publishedId);
                });
    }

    @Test
    void archivedFlashcardIsHiddenFromStudent() throws Exception {
        String adminToken = adminToken();
        long flashcardId = createPublishedFlashcard(adminToken,
                "Archive me", "再见", "Goodbye", DEFAULT_HSK_LEVEL, "greetings", 10);

        // Admin archives the flashcard.
        mockMvc.perform(authenticatedJsonPatch(
                adminFlashcardStatusPath(flashcardId),
                adminToken,
                Map.of(STATUS_FIELD, ARCHIVED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ARCHIVED));

        String studentToken = registerStudentAndLogin(uniqueEmail("archived-card-student"));

        mockMvc.perform(authenticatedGet(studentFlashcardPath(flashcardId), studentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(authenticatedPost(studentFlashcardCompletionPath(flashcardId), studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void libraryFilterByTypeAndTopicNarrowsResults() throws Exception {
        String adminToken = adminToken();

        String topicMarker = uniqueMarker("phase5topic");
        long flashcardId = createPublishedFlashcard(adminToken,
                "Marker Card", "蓝色", "blue", DEFAULT_HSK_LEVEL, topicMarker, 5);

        // Matching game in same topic.
        long matchingGameId = createPublishedMatchingGame(adminToken, "Marker Match", topicMarker);

        // Unrelated flashcard (different topic) must NOT leak through topic filter.
        createPublishedFlashcard(adminToken,
                "Other Topic Card", "红色", "red", DEFAULT_HSK_LEVEL, "colours-other", 5);

        String studentToken = registerStudentAndLogin(uniqueEmail("filter-student"));

        // Topic filter returns both items.
        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, studentToken)
                .param(TOPIC_PARAM, topicMarker))
                .andExpect(status().isOk())
                .andExpect(jsonPath(activityItemWithId(flashcardId)).exists())
                .andExpect(jsonPath(activityItemWithId(matchingGameId)).exists());

        // Type=FLASHCARD narrows to the card only.
        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, studentToken)
                .param(TOPIC_PARAM, topicMarker)
                .param(TYPE_PARAM, FLASHCARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath(activityItemWithId(flashcardId)).exists())
                .andExpect(jsonPath(activityItemWithId(matchingGameId)).doesNotExist());

        // Type=MATCHING_GAME narrows to the matching game only.
        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, studentToken)
                .param(TOPIC_PARAM, topicMarker)
                .param(TYPE_PARAM, MATCHING_GAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath(activityItemWithId(matchingGameId)).exists())
                .andExpect(jsonPath(activityItemWithId(flashcardId)).doesNotExist());
    }

    @Test
    void libraryFeedSortsAcrossActivityTypesByUpdatedAtDescending() throws Exception {
        String adminToken = adminToken();
        String topicMarker = uniqueMarker("global-sort");

        long olderFlashcardId = createPublishedFlashcard(adminToken,
                "Older Global Sort Card", "old", "older", DEFAULT_HSK_LEVEL, topicMarker, 5);
        long newerMatchingGameId = createPublishedMatchingGame(adminToken, "Newer Global Sort Match", topicMarker);
        setActivityUpdatedAt(ActivityTimestampTable.FLASHCARDS, olderFlashcardId, OLDER_ACTIVITY_TIME);
        setActivityUpdatedAt(ActivityTimestampTable.MATCHING_GAMES, newerMatchingGameId, NEWER_ACTIVITY_TIME);

        String studentToken = registerStudentAndLogin(uniqueEmail("global-sort-student"));

        MvcResult result = mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, studentToken)
                .param(TOPIC_PARAM, topicMarker)
                .param(SIZE_PARAM, PAGE_SIZE_TEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("id").asLong()).isEqualTo(newerMatchingGameId);
        assertThat(items.get(0).path("type").asText()).isEqualTo(MATCHING_GAME);
        assertThat(items.get(0).path("updatedAt").asText()).isNotBlank();
        assertThat(items.get(1).path("id").asLong()).isEqualTo(olderFlashcardId);
        assertThat(items.get(1).path("type").asText()).isEqualTo(FLASHCARD);
    }

    @Test
    void studentCanFetchMatchingGameWithItems() throws Exception {
        String adminToken = adminToken();
        long matchingGameId = createPublishedMatchingGame(adminToken, "Animals Match", "animals");

        String studentToken = registerStudentAndLogin(uniqueEmail("matching-student"));

        mockMvc.perform(authenticatedGet(studentMatchingGamePath(matchingGameId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].leftText").value("猫"));

        mockMvc.perform(authenticatedPost(studentMatchingGameCompletionPath(matchingGameId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstCompletion").value(true))
                .andExpect(jsonPath("$.data.type").value(MATCHING_GAME));
    }

    @Test
    void adminTokenIsForbiddenFromStudentEndpoints() throws Exception {
        String adminToken = adminToken();
        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentActivityLibraryShouldRejectOversizedFilterStrings() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("act-filter-boundary"));

        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, studentToken)
                .param(KEYWORD_PARAM, "x".repeat(OVERSIZED_FILTER_LENGTH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void studentActivityLibraryShouldRejectTooDeepPages() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("deep-page-student"));

        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, studentToken)
                .param(PAGE_PARAM, DEEP_PAGE)
                .param(SIZE_PARAM, PAGE_SIZE_ONE_HUNDRED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Requested activity page is too deep. Please use filters or a smaller page."));
    }

    @Test
    void teacherTokenIsForbiddenFromStudentEndpoints() throws Exception {
        String teacherEmail = uniqueEmail("teacher-blocked");
        registerTeacher(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, USER_PASSWORD);

        mockMvc.perform(authenticatedGet(STUDENT_ACTIVITIES_ENDPOINT, teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get(STUDENT_ACTIVITIES_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    /** Creates a flashcard with the given metadata and immediately publishes it. */
    private long createPublishedFlashcard(
            String adminToken,
            String title,
            String frontText,
            String backText,
            int hskLevel,
            String topic,
            int xpReward) throws Exception {
        long id = createFlashcard(adminToken, Map.ofEntries(
                Map.entry("title", title),
                Map.entry("frontText", frontText),
                Map.entry("backText", backText),
                Map.entry("hskLevel", hskLevel),
                Map.entry("topic", topic),
                Map.entry("xpReward", xpReward),
                Map.entry("estimatedSeconds", DEFAULT_FLASHCARD_SECONDS)));
        publishActivity(adminFlashcardStatusPath(id), adminToken);
        return id;
    }

    private long createFlashcard(String adminToken, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(authenticatedJsonPost(ADMIN_FLASHCARDS_ENDPOINT, adminToken, body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createPublishedMatchingGame(String adminToken, String title, String topic) throws Exception {
        Map<String, Object> body = Map.of(
                "title", title,
                "hskLevel", DEFAULT_HSK_LEVEL,
                "topic", topic,
                "xpReward", DEFAULT_MATCHING_XP,
                "estimatedSeconds", DEFAULT_MATCHING_SECONDS,
                "items", List.of(
                        Map.of("leftText", "猫", "rightText", "cat", "pinyin", "māo", "sortOrder", 0),
                        Map.of("leftText", "狗", "rightText", "dog", "pinyin", "gǒu", "sortOrder", 1)));
        MvcResult result = mockMvc.perform(authenticatedJsonPost(ADMIN_MATCHING_GAMES_ENDPOINT, adminToken, body))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        publishActivity(adminMatchingGameStatusPath(id), adminToken);
        return id;
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost(TEACHER_REGISTER_ENDPOINT, Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", TEACHER_BIO)))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(jsonPost(STUDENT_REGISTER_ENDPOINT, Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", DEFAULT_LANGUAGE)))
                .andExpect(status().isOk());
        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private String adminToken() throws Exception {
        return loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost(LOGIN_ENDPOINT, Map.of(
                        "email", email,
                        "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private void publishActivity(String statusPath, String adminToken) throws Exception {
        mockMvc.perform(authenticatedJsonPatch(statusPath, adminToken, Map.of(STATUS_FIELD, PUBLISHED)))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder authenticatedGet(String path, String token) {
        return get(path).header(AUTHORIZATION, BEARER + token);
    }

    private MockHttpServletRequestBuilder authenticatedPost(String path, String token) {
        return post(path).with(csrf()).header(AUTHORIZATION, BEARER + token);
    }

    private MockHttpServletRequestBuilder authenticatedJsonPost(String path, String token, Object body)
            throws Exception {
        return authenticatedPost(path, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private MockHttpServletRequestBuilder authenticatedJsonPatch(String path, String token, Object body)
            throws Exception {
        return patch(path)
                .with(csrf())
                .header(AUTHORIZATION, BEARER + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Object body) throws Exception {
        return post(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String studentFlashcardPath(long id) {
        return STUDENT_ACTIVITIES_ENDPOINT + "/flashcards/" + id;
    }

    private static String studentFlashcardCompletionPath(long id) {
        return studentFlashcardPath(id) + COMPLETE_SUFFIX;
    }

    private static String studentMatchingGamePath(long id) {
        return STUDENT_ACTIVITIES_ENDPOINT + "/matching-games/" + id;
    }

    private static String studentMatchingGameCompletionPath(long id) {
        return studentMatchingGamePath(id) + COMPLETE_SUFFIX;
    }

    private static String adminFlashcardStatusPath(long id) {
        return ADMIN_FLASHCARDS_ENDPOINT + "/" + id + "/status";
    }

    private static String adminMatchingGameStatusPath(long id) {
        return ADMIN_MATCHING_GAMES_ENDPOINT + "/" + id + "/status";
    }

    private static String activityItemWithId(long id) {
        return "$.data.items[?(@.id == " + id + ")]";
    }

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }

    private void setActivityUpdatedAt(ActivityTimestampTable table, long id, LocalDateTime updatedAt) {
        int rows = jdbcTemplate.update(table.updateStatement(), updatedAt, id);
        assertThat(rows).isEqualTo(1);
    }

    private enum ActivityTimestampTable {
        FLASHCARDS("UPDATE flashcards SET updated_at = ? WHERE id = ?"),
        MATCHING_GAMES("UPDATE matching_games SET updated_at = ? WHERE id = ?");

        private final String updateStatement;

        ActivityTimestampTable(String updateStatement) {
            this.updateStatement = updateStatement;
        }

        String updateStatement() {
            return updateStatement;
        }
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + EMAIL_DOMAIN;
    }

    private String uniqueMarker(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
