package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.XiaomeiMemoryNote;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.XiaomeiMemoryNoteRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.XiaomeiMemoryService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * What Talk with Xiaomei remembers, through the API with the AI provider off (S7.14, S7.15): the summary lists the goal of
 * the learner's plan also when SparkBot cannot write the rest, "Forget" takes a note away at once and its Undo brings it
 * back while the Undo window lasts, the plan's goal follows a new goal, and nobody reaches another learner's notes.
 * {@code AiProviderIntegrationTests} covers the facts SparkBot finds in a chat.
 */
class XiaomeiMemoryIntegrationTests extends ApiIntegrationTest {

    private static final String MEMORY = "/api/v1/ai/conversation/memory";
    private static final String NOT_REMEMBERED = "Xiaomei no longer remembers this.";

    @Autowired
    private XiaomeiMemoryNoteRepository noteRepository;

    @Autowired
    private LearningPlanRepository planRepository;

    @Autowired
    private XiaomeiMemoryService memory;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void overlappingSummariesRememberTheSameFactOnce() throws Exception {
        String email = uniqueEmail("xiaomei-concurrent");
        api.registerStudentAccount(email);
        SecurityUserDetails learner = principalFor(email);
        List<String> facts = List.of("Likes noodles");

        var second = withRowLock(jdbc, "users", new TransactionTemplate(transactions),
                () -> memory.remember(learner, facts), () -> memory.remember(learner, facts));
        assertThat(second).extracting(XiaomeiResponses.MemoryNote::text).containsExactly("Likes noodles");
        assertThat(notesOf(email)).extracting(XiaomeiMemoryNote::getText).containsExactly("Likes noodles");
    }

    @Test
    void theSummaryListsThePlanGoalWithoutTheAiAndForgetAndItsUndoWork() throws Exception {
        String token = api.registerStudent(uniqueEmail("xiaomei-memory-plan"));
        mockMvc.perform(api.authGet(MEMORY, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        api.startTravelPlan(token);

        JsonNode summary = api.data(mockMvc.perform(api.jsonPost("/api/v1/ai/conversation/summary", token, conversation()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(summary.path("status").asText()).isEqualTo("UNAVAILABLE");
        // A plan built from rules has no goal title: the page names its reason, as the plan pages do.
        assertThat(summary.path("memory")).singleElement().satisfies(note -> {
            assertThat(note.path("planReason").asText()).isEqualTo("TRAVEL");
            assertThat(note.path("text").isNull()).isTrue();
        });
        long goalId = summary.path("memory").get(0).path("id").asLong();

        mockMvc.perform(api.authDelete(MEMORY + "/" + goalId, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        // A forgotten goal stays forgotten: listing the notes again does not bring it back.
        mockMvc.perform(api.authGet(MEMORY, token)).andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(api.authPost(MEMORY + "/" + goalId + "/restore", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(goalId))
                .andExpect(jsonPath("$.data[0].planReason").value("TRAVEL"));
    }

    @Test
    void aNewGoalReplacesTheNoteOfTheGoalBefore() throws Exception {
        String email = uniqueEmail("xiaomei-memory-goal");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        mockMvc.perform(api.authGet(MEMORY, token)).andExpect(jsonPath("$.data[0].planReason").value("TRAVEL"));

        LearningPlan plan = planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(
                studentProfile(email).getId()).orElseThrow();
        plan.setGoalTitle("Travel to Shanghai");
        planRepository.save(plan);

        mockMvc.perform(api.authGet(MEMORY, token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].text").value("Travel to Shanghai"))
                .andExpect(jsonPath("$.data[0].planReason").value("TRAVEL"));
        assertThat(notesOf(email)).singleElement()
                .satisfies(note -> assertThat(note.getText()).isEqualTo("Travel to Shanghai"));
    }

    @Test
    void aForgottenChatNoteComesBackOnlyDuringTheUndoWindowAndIsThenDeletedForGood() throws Exception {
        String email = uniqueEmail("xiaomei-memory-fact");
        String token = api.registerStudent(email);
        User learner = user(email);
        XiaomeiMemoryNote fact = chatNote(learner, "Studies Chinese at school");
        XiaomeiMemoryNote other = chatNote(learner, "Likes bubble tea");

        mockMvc.perform(api.authGet(MEMORY, token))
                .andExpect(jsonPath("$.data[0].text").value("Studies Chinese at school"))
                .andExpect(jsonPath("$.data[0].planReason").doesNotExist())
                .andExpect(jsonPath("$.data[1].text").value("Likes bubble tea"));
        mockMvc.perform(api.authDelete(MEMORY + "/" + fact.getId(), token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(other.getId()));
        mockMvc.perform(api.authPost(MEMORY + "/" + fact.getId() + "/restore", token))
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(api.authDelete(MEMORY + "/" + fact.getId(), token)).andExpect(status().isOk());
        jdbc.update("update xiaomei_memory_notes set forgotten_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)), fact.getId());
        mockMvc.perform(api.authPost(MEMORY + "/" + fact.getId() + "/restore", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_REMEMBERED));
        assertThat(noteRepository.findById(fact.getId())).isEmpty();
        assertThat(notesOf(email)).extracting(XiaomeiMemoryNote::getText).containsExactly("Likes bubble tea");
    }

    @Test
    void notesAreTheLearnersOwn() throws Exception {
        String email = uniqueEmail("xiaomei-memory-owner");
        api.registerStudentAccount(email);
        XiaomeiMemoryNote fact = chatNote(user(email), "Studies Chinese at school");
        String otherToken = api.registerStudent(uniqueEmail("xiaomei-memory-other"));

        mockMvc.perform(api.authGet(MEMORY, otherToken)).andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(api.authDelete(MEMORY + "/" + fact.getId(), otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_REMEMBERED));
        mockMvc.perform(api.authPost(MEMORY + "/" + fact.getId() + "/restore", otherToken))
                .andExpect(status().isNotFound());
        assertThat(noteRepository.findById(fact.getId())).hasValueSatisfying(
                note -> assertThat(note.getForgottenAt()).isNull());
        // The AI Helper is for learners: admins have no Xiaomei.
        mockMvc.perform(api.authGet(MEMORY, api.adminToken())).andExpect(status().isForbidden());
    }

    @Test
    void deletingTheAccountDeletesEverythingXiaomeiRemembered() throws Exception {
        String email = uniqueEmail("xiaomei-memory-delete");
        String token = api.registerStudent(email);
        api.startTravelPlan(token);
        User learner = user(email);
        chatNote(learner, "Studies Chinese at school");
        XiaomeiMemoryNote forgotten = chatNote(learner, "Likes bubble tea");
        mockMvc.perform(api.authDelete(MEMORY + "/" + forgotten.getId(), token)).andExpect(status().isOk());
        Long userId = learner.getId();
        assertThat(noteRepository.findByStudentUserIdOrderByCreatedAtAscIdAsc(userId)).isNotEmpty();

        mockMvc.perform(api.jsonPost("/api/v1/users/me/delete", token, Map.of("confirmEmail", email)))
                .andExpect(status().isOk());

        // Remembered, forgotten and the plan's goal alike: nothing of what the learner told Xiaomei is kept.
        assertThat(noteRepository.findByStudentUserIdOrderByCreatedAtAscIdAsc(userId)).isEmpty();
    }

    private XiaomeiMemoryNote chatNote(User student, String text) {
        XiaomeiMemoryNote note = new XiaomeiMemoryNote();
        note.setStudentUser(student);
        note.setText(text);
        return noteRepository.save(note);
    }

    private List<XiaomeiMemoryNote> notesOf(String email) {
        return noteRepository.findByStudentUserIdOrderByCreatedAtAscIdAsc(user(email).getId());
    }

    private static Map<String, Object> conversation() {
        return Map.of(
                "scenario", "MEET_XIAOMEI",
                "turns", List.of(
                        Map.of("speaker", "XIAOMEI", "text", "你好！我是小美。今天过得怎么样？"),
                        Map.of("speaker", "STUDENT", "text", "我很好。我在学校学中文。")),
                "languagePreference", "en");
    }
}
