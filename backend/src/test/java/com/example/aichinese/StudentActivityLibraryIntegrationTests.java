package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @Test
    void studentCanListAndCompletePublishedFlashcardAndXpIsAwardedExactlyOnce() throws Exception {
        String adminToken = adminToken();

        // ---- admin creates two flashcards, only one is published ----
        long publishedId = createPublishedFlashcard(adminToken,
                "Greetings 你好", "你好", "Hello", 1, "greetings", 15);
        long draftId = createFlashcard(adminToken,
                Map.of("title", "Draft Flashcard", "frontText", "再见", "backText", "Goodbye"));

        // ---- student registers + lists library ----
        String studentEmail = uniqueEmail("activity-student");
        String studentToken = registerStudentAndLogin(studentEmail);

        mockMvc.perform(get("/api/v1/students/activities")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[?(@.id == " + publishedId + ")]").exists())
                // Draft must never leak through the student library feed.
                .andExpect(jsonPath("$.data.items[?(@.id == " + draftId + ")]").doesNotExist())
                // The `completed` flag is exposed on every summary; concrete
                // value is exercised by the detail-call assertion further down.
                .andExpect(jsonPath("$.data.items[?(@.id == " + publishedId + ")].completed").exists());

        // ---- student fetches detail ----
        mockMvc.perform(get("/api/v1/students/activities/flashcards/" + publishedId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frontText").value("你好"))
                .andExpect(jsonPath("$.data.xpReward").value(15))
                .andExpect(jsonPath("$.data.completed").value(false));

        // ---- student fetches draft -> 404 ----
        mockMvc.perform(get("/api/v1/students/activities/flashcards/" + draftId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        // ---- first completion awards xpReward ----
        mockMvc.perform(post("/api/v1/students/activities/flashcards/" + publishedId + "/complete")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnedPoints").value(15))
                .andExpect(jsonPath("$.data.totalPoints").value(15))
                .andExpect(jsonPath("$.data.firstCompletion").value(true))
                .andExpect(jsonPath("$.data.type").value("FLASHCARD"));

        // ---- second completion must NOT double-award ----
        mockMvc.perform(post("/api/v1/students/activities/flashcards/" + publishedId + "/complete")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnedPoints").value(0))
                .andExpect(jsonPath("$.data.totalPoints").value(15))
                .andExpect(jsonPath("$.data.firstCompletion").value(false));

        // ---- subsequent detail call should report completed=true ----
        mockMvc.perform(get("/api/v1/students/activities/flashcards/" + publishedId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        // ---- DB assertions: exactly one PLATFORM_FLASHCARD transaction ----
        StudentProfile student = findStudentProfile(studentEmail);
        assertThat(student.getTotalPoints()).isEqualTo(15);
        List<PointsTransaction> txs = pointsTransactionRepository
                .findByStudentIdOrderByCreatedAtDesc(student.getId());
        assertThat(txs)
                .filteredOn(tx -> "PLATFORM_FLASHCARD".equals(tx.getSourceType()))
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
                "Archive me", "再见", "Goodbye", 1, "greetings", 10);

        // Admin archives the flashcard.
        mockMvc.perform(patch("/api/v1/admin/activities/flashcards/" + flashcardId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        String studentToken = registerStudentAndLogin(uniqueEmail("archived-card-student"));

        mockMvc.perform(get("/api/v1/students/activities/flashcards/" + flashcardId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/students/activities/flashcards/" + flashcardId + "/complete")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void libraryFilterByTypeAndTopicNarrowsResults() throws Exception {
        String adminToken = adminToken();

        String topicMarker = "phase5topic-" + UUID.randomUUID().toString().substring(0, 6);
        long flashcardId = createPublishedFlashcard(adminToken,
                "Marker Card", "蓝色", "blue", 1, topicMarker, 5);

        // Matching game in same topic.
        long matchingGameId = createPublishedMatchingGame(adminToken, "Marker Match", topicMarker);

        // Unrelated flashcard (different topic) must NOT leak through topic filter.
        createPublishedFlashcard(adminToken,
                "Other Topic Card", "红色", "red", 1, "colours-other", 5);

        String studentToken = registerStudentAndLogin(uniqueEmail("filter-student"));

        // Topic filter returns both items.
        mockMvc.perform(get("/api/v1/students/activities")
                .param("topic", topicMarker)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + flashcardId + ")]").exists())
                .andExpect(jsonPath("$.data.items[?(@.id == " + matchingGameId + ")]").exists());

        // Type=FLASHCARD narrows to the card only.
        mockMvc.perform(get("/api/v1/students/activities")
                .param("topic", topicMarker)
                .param("type", "FLASHCARD")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + flashcardId + ")]").exists())
                .andExpect(jsonPath("$.data.items[?(@.id == " + matchingGameId + ")]").doesNotExist());

        // Type=MATCHING_GAME narrows to the matching game only.
        mockMvc.perform(get("/api/v1/students/activities")
                .param("topic", topicMarker)
                .param("type", "MATCHING_GAME")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + matchingGameId + ")]").exists())
                .andExpect(jsonPath("$.data.items[?(@.id == " + flashcardId + ")]").doesNotExist());
    }

    @Test
    void libraryFeedSortsAcrossActivityTypesByUpdatedAtDescending() throws Exception {
        String adminToken = adminToken();
        String topicMarker = "global-sort-" + UUID.randomUUID().toString().substring(0, 6);

        long olderFlashcardId = createPublishedFlashcard(adminToken,
                "Older Global Sort Card", "old", "older", 1, topicMarker, 5);
        Thread.sleep(20);
        long newerMatchingGameId = createPublishedMatchingGame(adminToken, "Newer Global Sort Match", topicMarker);

        String studentToken = registerStudentAndLogin(uniqueEmail("global-sort-student"));

        MvcResult result = mockMvc.perform(get("/api/v1/students/activities")
                .param("topic", topicMarker)
                .param("size", "10")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("id").asLong()).isEqualTo(newerMatchingGameId);
        assertThat(items.get(0).path("type").asText()).isEqualTo("MATCHING_GAME");
        assertThat(items.get(0).path("updatedAt").asText()).isNotBlank();
        assertThat(items.get(1).path("id").asLong()).isEqualTo(olderFlashcardId);
        assertThat(items.get(1).path("type").asText()).isEqualTo("FLASHCARD");
    }

    @Test
    void studentCanFetchMatchingGameWithItems() throws Exception {
        String adminToken = adminToken();
        long matchingGameId = createPublishedMatchingGame(adminToken, "Animals Match", "animals");

        String studentToken = registerStudentAndLogin(uniqueEmail("matching-student"));

        mockMvc.perform(get("/api/v1/students/activities/matching-games/" + matchingGameId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].leftText").value("猫"));

        mockMvc.perform(post("/api/v1/students/activities/matching-games/" + matchingGameId + "/complete")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstCompletion").value(true))
                .andExpect(jsonPath("$.data.type").value("MATCHING_GAME"));
    }

    @Test
    void adminTokenIsForbiddenFromStudentEndpoints() throws Exception {
        String adminToken = adminToken();
        mockMvc.perform(get("/api/v1/students/activities")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentActivityLibraryShouldRejectOversizedFilterStrings() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("act-filter-boundary"));

        mockMvc.perform(get("/api/v1/students/activities")
                .param("keyword", "x".repeat(201))
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherTokenIsForbiddenFromStudentEndpoints() throws Exception {
        String teacherEmail = uniqueEmail("teacher-blocked");
        registerTeacher(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        mockMvc.perform(get("/api/v1/students/activities")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/students/activities"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------

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
                Map.entry("estimatedSeconds", 30)));
        mockMvc.perform(patch("/api/v1/admin/activities/flashcards/" + id + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk());
        return id;
    }

    private long createFlashcard(String adminToken, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/activities/flashcards")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createPublishedMatchingGame(String adminToken, String title, String topic) throws Exception {
        Map<String, Object> body = Map.of(
                "title", title,
                "hskLevel", 1,
                "topic", topic,
                "xpReward", 20,
                "estimatedSeconds", 60,
                "items", List.of(
                        Map.of("leftText", "猫", "rightText", "cat", "pinyin", "māo", "sortOrder", 0),
                        Map.of("leftText", "狗", "rightText", "dog", "pinyin", "gǒu", "sortOrder", 1)));
        MvcResult result = mockMvc.perform(post("/api/v1/admin/activities/matching-games")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/activities/matching-games/" + id + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk());
        return id;
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());
        return loginAndExtractToken(email, "Password123!");
    }

    private String adminToken() throws Exception {
        return loginAndExtractToken("admin@example.com", "SuperSecure123!");
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
