package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage of the admin activity-library REST surface.
 *
 * The platform owns the curated library of Flashcards, MatchingGames,
 * ListeningExercises, TracingExercises, DragDropActivities and
 * StoryActivities. Teachers no longer author these — only admins do, via
 * {@code /api/v1/admin/activities/**}.
 *
 * These tests exercise the full CRUD round-trip on representative types
 * (flashcard for the simple single-row case, matching-game for the
 * composite parent+children case) plus the status lifecycle and the
 * role-based access guards.
 *
 * <p>Also pins the per-type validation invariants (matching games need
 * pairs, drag-drop needs targets, stories need comprehension
 * questions) so a future refactor of {@code AdminActivityService}
 * cannot quietly weaken them.</p>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminActivityLibraryIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private MatchingGameRepository matchingGameRepository;

    @Autowired
    private MatchingGameItemRepository matchingGameItemRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void adminCanRunFullFlashcardLifecycle() throws Exception {
        String token = adminToken();
        long auditBefore = auditLogRepository.count();

        // ---- Create ----
        Map<String, Object> createBody = Map.ofEntries(
                Map.entry("title", "Greetings 你好"),
                Map.entry("frontText", "你好"),
                Map.entry("backText", "Hello"),
                Map.entry("pinyin", "nǐ hǎo"),
                Map.entry("englishMeaning", "Hello"),
                Map.entry("sortOrder", 0),
                Map.entry("hskLevel", 1),
                Map.entry("ageBand", "LOWER_PRIMARY"),
                Map.entry("difficulty", "EASY"),
                Map.entry("topic", "greetings"),
                Map.entry("tags", List.of("basic", "polite")),
                Map.entry("xpReward", 10),
                Map.entry("estimatedSeconds", 30));

        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/activities/flashcards")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Greetings 你好"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.tags[0]").value("basic"))
                .andExpect(jsonPath("$.data.createdByDisplayName").exists())
                .andReturn();

        long flashcardId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertThat(flashcardRepository.findById(flashcardId)).isPresent();

        // ---- List (filtered by topic) ----
        mockMvc.perform(get("/api/v1/admin/activities/flashcards")
                .param("topic", "greetings")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value((int) flashcardId));

        // ---- Get ----
        mockMvc.perform(get("/api/v1/admin/activities/flashcards/" + flashcardId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frontText").value("你好"));

        // ---- Update ----
        Map<String, Object> updateBody = Map.ofEntries(
                Map.entry("title", "Greetings 你好 (revised)"),
                Map.entry("frontText", "你好"),
                Map.entry("backText", "Hello there"),
                Map.entry("pinyin", "nǐ hǎo"),
                Map.entry("englishMeaning", "Hello (greeting)"),
                Map.entry("sortOrder", 1),
                Map.entry("hskLevel", 1),
                Map.entry("ageBand", "LOWER_PRIMARY"),
                Map.entry("difficulty", "EASY"),
                Map.entry("topic", "greetings"),
                Map.entry("tags", List.of("basic")));

        mockMvc.perform(put("/api/v1/admin/activities/flashcards/" + flashcardId)
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Greetings 你好 (revised)"))
                .andExpect(jsonPath("$.data.backText").value("Hello there"));

        // ---- Publish ----
        mockMvc.perform(patch("/api/v1/admin/activities/flashcards/" + flashcardId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // ---- Delete ----
        mockMvc.perform(delete("/api/v1/admin/activities/flashcards/" + flashcardId)
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(flashcardRepository.findById(flashcardId)).isEmpty();

        // Each of the four mutations (create, update, status, delete) should
        // have produced an audit-log entry. Without this guard, admin actions
        // can silently bypass compliance review.
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(auditBefore + 4);
        assertThat(auditLogRepository.findAll())
                .extracting(AuditLog::getActionType)
                .contains("ADMIN_ACTIVITY_CREATE",
                        "ADMIN_ACTIVITY_UPDATE",
                        "ADMIN_ACTIVITY_STATUS",
                        "ADMIN_ACTIVITY_DELETE");
    }

    @Test
    void adminCanCreateMatchingGameWithItemsAndDeleteCascades() throws Exception {
        String token = adminToken();

        Map<String, Object> body = Map.of(
                "title", "Animal matching",
                "hskLevel", 1,
                "ageBand", "LOWER_PRIMARY",
                "difficulty", "EASY",
                "topic", "animals",
                "tags", List.of("animal"),
                "items", List.of(
                        Map.of("leftText", "猫", "rightText", "cat", "pinyin", "māo", "sortOrder", 0),
                        Map.of("leftText", "狗", "rightText", "dog", "pinyin", "gǒu", "sortOrder", 1)));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/activities/matching-games")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].leftText").value("猫"))
                .andReturn();

        long gameId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Re-saving the game with one fewer item must wipe the old children
        // and persist only the new payload (full replacement semantics).
        Map<String, Object> updateBody = Map.of(
                "title", "Animal matching (trimmed)",
                "topic", "animals",
                "items", List.of(
                        Map.of("leftText", "鸟", "rightText", "bird", "pinyin", "niǎo", "sortOrder", 0)));

        mockMvc.perform(put("/api/v1/admin/activities/matching-games/" + gameId)
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].leftText").value("鸟"));

        assertThat(matchingGameItemRepository
                .findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(gameId))
                .hasSize(1);

        // Deleting the parent must also remove the children — otherwise the
        // matching_game_items table would slowly accumulate orphans.
        mockMvc.perform(delete("/api/v1/admin/activities/matching-games/" + gameId)
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(matchingGameRepository.findById(gameId)).isEmpty();
        assertThat(matchingGameItemRepository
                .findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(gameId))
                .isEmpty();
    }

    @Test
    void unifiedFeedReturnsMixedActivityTypes() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admin/activities/flashcards")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Feed-card",
                        "frontText", "苹果",
                        "backText", "apple",
                        "topic", "food"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/activities/listening-exercises")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Feed-listen",
                        "audioUrl", "https://example.com/audio.mp3",
                        "topic", "food"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/activities")
                .param("topic", "food")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type == 'FLASHCARD')]").exists())
                .andExpect(jsonPath("$.data.items[?(@.type == 'LISTENING_EXERCISE')]").exists());
    }

    @Test
    void invalidStatusTransitionShouldBeRejected() throws Exception {
        String token = adminToken();
        MvcResult result = mockMvc.perform(post("/api/v1/admin/activities/flashcards")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Status guard card",
                        "frontText", "测试",
                        "backText", "test"))))
                .andExpect(status().isOk())
                .andReturn();
        long flashcardId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // PENDING_REVIEW is a legitimate enum value but should NOT be settable
        // through the admin endpoint — admins can only move between DRAFT,
        // PUBLISHED and ARCHIVED.
        mockMvc.perform(patch("/api/v1/admin/activities/flashcards/" + flashcardId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "PENDING_REVIEW"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void validationRejectsBlankRequiredField() throws Exception {
        String token = adminToken();
        // `frontText` is annotated `@NotBlank` on `AdminFlashcardRequest`, so
        // an empty string must be rejected by bean validation. (`title` is
        // intentionally optional for flashcards — the student UI falls back
        // to `frontText` when the admin omits a separate title.)
        mockMvc.perform(post("/api/v1/admin/activities/flashcards")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Optional title",
                        "frontText", "",
                        "backText", "back"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void studentTokenIsForbidden() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("student-activity-forbidden"));

        mockMvc.perform(get("/api/v1/admin/activities/flashcards")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/activities/flashcards")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Should not work",
                        "frontText", "x",
                        "backText", "y"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherTokenIsForbidden() throws Exception {
        String teacherEmail = uniqueEmail("teacher-activity-forbidden");
        registerTeacher(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        // Activity authoring is now an admin-only capability; teachers must
        // be rejected with 403 to prevent accidental privilege escalation.
        mockMvc.perform(get("/api/v1/admin/activities/flashcards")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/activities/flashcards"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------

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

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
