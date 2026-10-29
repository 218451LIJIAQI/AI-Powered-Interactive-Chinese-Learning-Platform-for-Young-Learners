package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.support.ApiIntegrationTest;

/** The admin Learning path (units, level check bank) and the admin Practice library. */
class AdminLearningContentIntegrationTests extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/learning-path";
    private static final String LIBRARY = "/api/v1/admin/practice-sets";

    @Autowired
    private LearnUnitRepository unitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void adminsSeeEveryUnitAndPublishingChangesWhatStudentsSee() throws Exception {
        String admin = api.adminToken();
        mockMvc.perform(api.authGet(PATH, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitCount").value(8))
                .andExpect(jsonPath("$.data.lessonCount").value(47))
                .andExpect(jsonPath("$.data.levels[0].lessonCount").value(24))
                .andExpect(jsonPath("$.data.levels[1].lessonCount").value(23))
                .andExpect(jsonPath("$.data.levels[1].units[2].status").value("DRAFT"));

        LearnUnit unitThree = unitRepository.findAllByOrderByPositionAsc().get(2);
        mockMvc.perform(api.authGet(PATH + "/units/" + unitThree.getId(), admin))
                .andExpect(jsonPath("$.data.unit.title").value("Getting around"))
                .andExpect(jsonPath("$.data.lessons.length()").value(6))
                .andExpect(jsonPath("$.data.lessons[0].questionCount").isNumber());

        String student = api.registerStudent(uniqueEmail("admin-path-student"));
        try {
            mockMvc.perform(api.authPost(PATH + "/units/" + unitThree.getId() + "/unpublish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.unit.status").value("DRAFT"));
            mockMvc.perform(api.authPost(PATH + "/units/" + unitThree.getId() + "/unpublish", admin))
                    .andExpect(status().isBadRequest());
            JsonNode path = api.data(mockMvc.perform(api.authGet("/api/v1/students/learn/path", student)).andReturn());
            assertThat(path.findValuesAsText("title")).doesNotContain("Getting around");
            assertThat(auditLogRepository.findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc("LEARN_UNIT", unitThree.getId()))
                    .isNotEmpty();
        } finally {
            mockMvc.perform(api.authPost(PATH + "/units/" + unitThree.getId() + "/publish", admin))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void levelCheckQuestionsNeedExactlyOneRightAnswer() throws Exception {
        String admin = api.adminToken();
        Map<String, Object> question = Map.of(
                "prompt", "What does 你好 mean? " + uniqueSuffix(),
                "hanzi", "你好",
                "skill", "VOCABULARY",
                "difficulty", "EASY",
                "level", "BEGINNER_1",
                "status", "DRAFT",
                "options", List.of(
                        Map.of("text", "Hello", "correct", true, "explanation", "nǐ hǎo = hello"),
                        Map.of("text", "Goodbye", "correct", false, "explanation", "That's 再见")));
        Long id = api.id(mockMvc.perform(api.jsonPost(PATH + "/level-check-questions", admin, question))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.options[0].correct").value(true))
                .andExpect(jsonPath("$.data.correctPercent").doesNotExist())
                .andReturn());

        Map<String, Object> twoRight = new java.util.HashMap<>(question);
        twoRight.put("options", List.of(
                Map.of("text", "Hello", "correct", true),
                Map.of("text", "Hi", "correct", true)));
        mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, twoRight))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tick exactly one right answer."));

        Map<String, Object> published = new java.util.HashMap<>(question);
        published.put("status", "PUBLISHED");
        mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, published))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(api.authGet(PATH + "/level-check-questions", admin).param("size", "100"))
                .andExpect(jsonPath("$.data.items[?(@.id==" + id + ")].status").value("PUBLISHED"));
        // Kept out of the student check again so other tests see the seeded bank.
        mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void thePracticeLibraryListsFiltersAndShowsSets() throws Exception {
        String admin = api.adminToken();
        mockMvc.perform(api.authGet(LIBRARY, admin).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(35)))
                .andExpect(jsonPath("$.data.items.length()").value(10));
        JsonNode quizzes = api.data(mockMvc.perform(api.authGet(LIBRARY, admin).param("type", "quizzes").param("size", "50"))
                .andReturn());
        quizzes.path("items").forEach(row -> assertThat(row.path("type").asText()).isEqualTo("QUIZZES"));
        mockMvc.perform(api.authGet(LIBRARY, admin).param("q", "campus words"))
                .andExpect(jsonPath("$.data.items[0].title").value("Campus Words"))
                .andExpect(jsonPath("$.data.items[0].itemCount").value(6));
        mockMvc.perform(api.authGet(LIBRARY, admin).param("type", "poems"))
                .andExpect(status().isNotFound());

        long setId = api.data(mockMvc.perform(api.authGet(LIBRARY, admin).param("q", "At the Café")).andReturn())
                .path("items").get(0).path("id").asLong();
        mockMvc.perform(api.authGet(LIBRARY + "/listening/" + setId, admin))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].audioText").value("一杯茶"))
                .andExpect(jsonPath("$.data.items[0].correctIndex").value(0))
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.playedBy").isNumber())
                .andExpect(jsonPath("$.data.history[0].action").value("CREATED"));
    }

    @Test
    void newSetsStartAsDraftsAndMoveThroughPublishUnpublishArchiveRestore() throws Exception {
        String admin = api.adminToken();
        String student = api.registerStudent(uniqueEmail("library-student"));
        String title = "Colours Quiz " + uniqueSuffix();
        Map<String, Object> body = Map.of(
                "title", title,
                "stage", "GUIDED",
                "hskLevel", 1,
                "items", List.of(Map.of(
                        "prompt", "What does 红色 mean?",
                        "hanzi", "红色",
                        "pinyin", "hóngsè",
                        "options", List.of(Map.of("text", "red"), Map.of("text", "blue")),
                        "correctIndex", 0,
                        "explanation", "红 is red.")));
        mockMvc.perform(api.jsonPost(LIBRARY + "/quizzes", admin, Map.of("title", title, "items", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least one item to this set."));
        Long id = api.id(mockMvc.perform(api.jsonPost(LIBRARY + "/quizzes", admin, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.xpReward").value(15))
                .andReturn());
        String studentPath = "/api/v1/students/practice/quizzes/sets/" + id;
        mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());

        mockMvc.perform(api.authPost(LIBRARY + "/quizzes/" + id + "/archive", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only a published set can be archived."));
        mockMvc.perform(api.authPost(LIBRARY + "/quizzes/" + id + "/publish", admin))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isOk());

        mockMvc.perform(api.authPost(LIBRARY + "/quizzes/" + id + "/unpublish", admin))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());

        mockMvc.perform(api.authPost(LIBRARY + "/quizzes/" + id + "/publish", admin));
        mockMvc.perform(api.authPost(LIBRARY + "/quizzes/" + id + "/archive", admin))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());
        mockMvc.perform(api.authPost(LIBRARY + "/quizzes/" + id + "/restore", admin))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.history[0].action").value("RESTORED"))
                .andExpect(jsonPath("$.data.history.length()").value(6));

        Map<String, Object> edited = new java.util.HashMap<>(body);
        edited.put("title", title + " (edited)");
        mockMvc.perform(api.jsonPut(LIBRARY + "/quizzes/" + id, admin, edited))
                .andExpect(jsonPath("$.data.title").value(title + " (edited)"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // Done with it: archived so the student-facing lists keep the seeded five sets.
        mockMvc.perform(api.authPost(LIBRARY + "/quizzes/" + id + "/archive", admin));
    }

    @Test
    void eachTypeChecksItsOwnItems() throws Exception {
        String admin = api.adminToken();
        mockMvc.perform(api.jsonPost(LIBRARY + "/matching", admin, Map.of("title", "Pairs", "items", List.of(
                        Map.of("hanzi", "一", "meaning", "one")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least 2 pairs."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/drag-drop", admin, Map.of("title", "Sentences", "items", List.of(
                        Map.of("hanzi", "我", "meaning", "I")))))
                .andExpect(jsonPath("$.message").value("Every sentence needs at least 2 tiles and a meaning."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/tracing", admin, Map.of("title", "Write 人", "items", List.of(
                        Map.of("hanzi", "人")))))
                .andExpect(jsonPath("$.message").value("Enter how many strokes the characters have."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, Map.of("title", "Hear it", "items", List.of(
                        Map.of("prompt", "Which?", "options", List.of(Map.of("text", "a"), Map.of("text", "b")),
                                "correctIndex", 0)))))
                .andExpect(jsonPath("$.message").value("Every listening question needs the Chinese that is read aloud."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/story", admin, Map.of("title", "A story", "items", List.of())))
                .andExpect(jsonPath("$.message").value("Add the story text."));
    }

    @Test
    void onlyAdminsManageLearningContent() throws Exception {
        String teacher = api.approvedTeacher(uniqueEmail("library-teacher"));
        String student = api.registerStudent(uniqueEmail("library-other-student"));
        mockMvc.perform(api.authGet(LIBRARY, teacher)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(PATH, student)).andExpect(status().isForbidden());
        mockMvc.perform(api.jsonPost(LIBRARY + "/flashcards", teacher, Map.of("title", "Mine"))).andExpect(status().isForbidden());
    }
}
