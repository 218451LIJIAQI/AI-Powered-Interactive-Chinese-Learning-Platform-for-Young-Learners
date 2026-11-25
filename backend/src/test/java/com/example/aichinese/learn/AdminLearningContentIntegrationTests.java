package com.example.aichinese.learn;

import static com.example.aichinese.support.ConcurrentTransactions.readCommitted;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.LearningPathAdminService;
import com.example.aichinese.service.learn.PracticeLibraryService;
import com.example.aichinese.support.ApiIntegrationTest;

/** The admin Learning path (units, level check bank) and the admin Practice library. */
class AdminLearningContentIntegrationTests extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/learning-path";
    private static final String LIBRARY = "/api/v1/admin/practice-sets";

    @Autowired
    private LearnUnitRepository unitRepository;

    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Autowired
    private LearnClock clock;

    @Autowired
    private PracticeLibraryService library;

    @Autowired
    private LearningPathAdminService learningPath;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void adminsSeeEveryUnitAndPublishingChangesWhatStudentsSee() throws Exception {
        String admin = api.adminToken();
        mockMvc.perform(api.authGet(PATH, admin))
                .andExpect(status().isOk())
                // Lessons and reviews only, as on the student path: a bonus chest or a checkpoint is not a lesson.
                .andExpect(jsonPath("$.data.unitCount").value(8))
                .andExpect(jsonPath("$.data.levels[0].units[*].lessonCount").value(contains(7, 5, 7, 6)))
                .andExpect(jsonPath("$.data.levels[1].units[*].lessonCount").value(contains(5, 5, 5, 5)))
                // The row says how many chests a unit has beside its lessons (units 4 and 7 have none), so the page can
                // show "7 lessons + bonus chest".
                .andExpect(jsonPath("$.data.levels[0].units[*].bonusCount").value(contains(1, 1, 1, 0)))
                .andExpect(jsonPath("$.data.levels[1].units[*].bonusCount").value(contains(1, 1, 0, 1)))
                .andExpect(jsonPath("$.data.levels[1].units[1].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.levels[1].units[2].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.levels[1].units[3].status").value("DRAFT"));

        List<LearnUnit> units = unitRepository.findAllByOrderByPositionAsc();
        LearnUnit unitThree = units.get(2);
        String unitThreePath = PATH + "/units/" + unitThree.getId();
        mockMvc.perform(api.authGet(unitThreePath, admin))
                .andExpect(jsonPath("$.data.unit.title").value("First sentences"))
                .andExpect(jsonPath("$.data.unit.lessonCount").value(7))
                .andExpect(jsonPath("$.data.unit.bonusCount").value(1))
                .andExpect(jsonPath("$.data.lessons.length()").value(8))
                .andExpect(jsonPath("$.data.lessons[?(@.kind == 'BONUS')]").value(hasSize(1)));

        LearnUnit unitFour = units.get(3);
        mockMvc.perform(api.authGet(PATH + "/units/" + unitFour.getId(), admin))
                .andExpect(jsonPath("$.data.unit.lessonCount").value(6))
                .andExpect(jsonPath("$.data.unit.bonusCount").value(0));

        String student = api.registerStudent(uniqueEmail("admin-path-student"));
        try {
            String requestId = mockMvc.perform(api.authPost(unitThreePath + "/unpublish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.unit.status").value("DRAFT"))
                    .andReturn().getResponse().getHeader("X-Request-Id");
            mockMvc.perform(api.authPost(unitThreePath + "/unpublish", admin))
                    .andExpect(status().isBadRequest());
            JsonNode path = api.data(mockMvc.perform(api.authGet("/api/v1/students/learn/path", student))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(path.findValuesAsText("title")).contains("Pinyin and tones").doesNotContain("First sentences");
            assertThat(auditForRequest(requestId, "LEARN_UNIT_UNPUBLISH")).hasValueSatisfying(entry -> {
                assertThat(entry.getTargetType()).isEqualTo("LEARN_UNIT");
                assertThat(entry.getTargetId()).isEqualTo(unitThree.getId());
            });
        } finally {
            mockMvc.perform(api.authPost(unitThreePath + "/publish", admin));
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

        Map<String, Object> twoRight = new HashMap<>(question);
        twoRight.put("options", List.of(
                Map.of("text", "Hello", "correct", true),
                Map.of("text", "Hi", "correct", true)));
        mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, twoRight))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tick exactly one right answer."));

        Map<String, Object> duplicates = new HashMap<>(question);
        duplicates.put("options", List.of(
                Map.of("text", "Hello", "correct", false),
                Map.of("text", "\u00a0Hello　", "correct", true)));
        mockMvc.perform(api.jsonPost(PATH + "/level-check-questions", admin, duplicates))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The answers of a question must all be different."));
        // Earlier releases accepted duplicate labels. Reopening a saved draft for publication must check them too.
        jdbc.update("update level_check_options set option_text = ? where question_id = ? and correct = false",
                " Hello ", id);
        mockMvc.perform(api.authPost(PATH + "/level-check-questions/" + id + "/publish", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The answers of a question must all be different."));
        assertThat(questionAudit(id)).isEmpty();

        Map<String, Object> published = new HashMap<>(question);
        published.put("status", "PUBLISHED");
        boolean cleanedUp = false;
        try {
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, published))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
            mockMvc.perform(api.authGet(PATH + "/level-check-questions", admin).param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.id==" + id + ")].status").value("PUBLISHED"));
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, published))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
            cleanedUp = true;
        } finally {
            if (!cleanedUp) {
                mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question));
            }
        }
        // The draft being written is not audited; going live, an edit of the live question and going back are.
        assertThat(questionAudit(id)).containsExactlyInAnyOrder(
                "LEVEL_CHECK_QUESTION_PUBLISH", "LEVEL_CHECK_QUESTION_UPDATE", "LEVEL_CHECK_QUESTION_UNPUBLISH");
    }

    /** Publish from a row can be undone: the question goes back to a draft, and both steps are audited. */
    @Test
    void aPublishedQuestionGoesBackToADraftWithUndo() throws Exception {
        String admin = api.adminToken();
        Map<String, Object> question = Map.of(
                "prompt", "What does 谢谢 mean? " + uniqueSuffix(),
                "skill", "VOCABULARY", "difficulty", "EASY", "level", "BEGINNER_1", "status", "DRAFT",
                "options", List.of(Map.of("text", "Thank you", "correct", true), Map.of("text", "Sorry", "correct", false)));
        Long id = api.id(mockMvc.perform(api.jsonPost(PATH + "/level-check-questions", admin, question))
                .andExpect(status().isOk()).andReturn());
        String questionPath = PATH + "/level-check-questions/" + id;

        try {
            mockMvc.perform(api.authPost(questionPath + "/publish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
            mockMvc.perform(api.authPost(questionPath + "/unpublish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Question unpublished."))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.options[0].text").value("Thank you"));
            // A draft already stays one, without another entry.
            mockMvc.perform(api.authPost(questionPath + "/unpublish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
            assertThat(questionAudit(id))
                    .containsExactlyInAnyOrder("LEVEL_CHECK_QUESTION_PUBLISH", "LEVEL_CHECK_QUESTION_UNPUBLISH");
            mockMvc.perform(api.authPost(PATH + "/level-check-questions/999999/unpublish", admin))
                    .andExpect(status().isNotFound());
        } finally {
            mockMvc.perform(api.authPost(questionPath + "/unpublish", admin));
        }
    }

    private List<String> questionAudit(Long questionId) {
        return auditEntriesAbout("LEVEL_CHECK_QUESTION", questionId).stream()
                .map(AuditLog::getActionType)
                .toList();
    }

    @Test
    void onlyTheFirstOverlappingRequestPublishesTheSameLevelCheckQuestion() throws Exception {
        String token = api.adminToken();
        var admin = principalFor(ADMIN_EMAIL);
        Map<String, Object> question = Map.of(
                "prompt", "Choose hello " + uniqueSuffix(), "skill", "VOCABULARY", "difficulty", "EASY",
                "level", "BEGINNER_1", "status", "DRAFT",
                "options", List.of(Map.of("text", "你好", "correct", true), Map.of("text", "再见", "correct", false)));
        String bank = PATH + "/level-check-questions";
        long id = api.id(mockMvc.perform(api.jsonPost(bank, token, question)).andExpect(status().isOk()).andReturn());
        boolean cleanedUp = false;
        try {
            assertSecondPublishRejected("level_check_questions",
                    () -> assertThat(learningPath.publishQuestion(admin, id).status()).isEqualTo(PublishStatus.PUBLISHED),
                    bank + "/" + id + "/publish", token, "This question is already published.");
            assertThat(questionAudit(id)).containsExactly("LEVEL_CHECK_QUESTION_PUBLISH");
            mockMvc.perform(api.authPost(bank + "/" + id + "/unpublish", token)).andExpect(status().isOk());
            cleanedUp = true;
        } finally {
            if (!cleanedUp) {
                mockMvc.perform(api.authPost(bank + "/" + id + "/unpublish", token));
            }
        }
    }

    @Test
    void theLevelCheckBankListsEasyThenMediumThenHardWithinEachLevel() throws Exception {
        JsonNode rows = api.data(mockMvc.perform(api.authGet(PATH + "/level-check-questions", api.adminToken()).param("size", "100"))
                .andExpect(status().isOk())
                .andReturn()).path("items");
        List<String> shown = rows.valueStream()
                .map(row -> row.path("level").asText() + " " + row.path("difficulty").asText())
                .toList();

        // The seeded bank has every step, so an alphabetical order (hard before medium) would show here.
        List<String> order = List.of("BEGINNER_1 EASY", "BEGINNER_1 MEDIUM", "BEGINNER_1 HARD",
                "BEGINNER_2 EASY", "BEGINNER_2 MEDIUM", "BEGINNER_2 HARD");
        assertThat(shown).containsAll(order).isSortedAccordingTo(Comparator.comparingInt(order::indexOf));
    }

    @Test
    void listeningQuestionsNeedAudioBeforePublishingAndFailedUpdatesKeepTheirOptions() throws Exception {
        String admin = api.adminToken();
        Map<String, Object> question = new HashMap<>(Map.of(
                "prompt", "Listen and choose " + uniqueSuffix(),
                "skill", "LISTENING", "difficulty", "EASY", "level", "BEGINNER_1", "status", "DRAFT",
                "options", List.of(Map.of("text", "Hello", "correct", true), Map.of("text", "Goodbye", "correct", false))));
        Long id = api.id(mockMvc.perform(api.jsonPost(PATH + "/level-check-questions", admin, question))
                .andExpect(status().isOk()).andReturn());
        question.put("status", "PUBLISHED");
        question.put("audioText", "\u00a0　");
        mockMvc.perform(api.jsonPost(PATH + "/level-check-questions", admin, question))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every listening question needs the Chinese that is read aloud."));
        mockMvc.perform(api.authGet(PATH + "/level-check-questions/" + id, admin))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.options[0].text").value("Hello"))
                .andExpect(jsonPath("$.data.options[0].correct").value(true));
        // Publishing the saved question checks it as it is saved: it has no audio yet.
        mockMvc.perform(api.authPost(PATH + "/level-check-questions/" + id + "/publish", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every listening question needs the Chinese that is read aloud."));
        question.put("audioText", "  你好  ");
        // The answers keep their option rows (and ids) by position: a student part-way through a level check keeps
        // the answers already given when a question is corrected.
        List<Long> optionIds = levelCheckOptionRepository.findByQuestionIdOrderByPositionAscIdAsc(id).stream()
                .map(LevelCheckOption::getId)
                .toList();
        boolean cleanedUp = false;
        try {
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.audioText").value("你好"));
            assertThat(levelCheckOptionRepository.findByQuestionIdOrderByPositionAscIdAsc(id))
                    .extracting(LevelCheckOption::getId)
                    .containsExactlyElementsOf(optionIds);

            question.put("options", List.of(
                    Map.of("text", "Hello!", "correct", true),
                    Map.of("text", "Goodbye", "correct", false),
                    Map.of("text", "Thank you", "correct", false)));
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.options[0].text").value("Hello!"))
                    .andExpect(jsonPath("$.data.options.length()").value(3));
            List<LevelCheckOption> grown = levelCheckOptionRepository.findByQuestionIdOrderByPositionAscIdAsc(id);
            assertThat(grown.subList(0, 2)).extracting(LevelCheckOption::getId).containsExactlyElementsOf(optionIds);
            question.put("options", List.of(Map.of("text", "Hello", "correct", true), Map.of("text", "Hi", "correct", false)));
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                    .andExpect(jsonPath("$.data.options.length()").value(2));
            assertThat(levelCheckOptionRepository.findByQuestionIdOrderByPositionAscIdAsc(id))
                    .extracting(LevelCheckOption::getId)
                    .containsExactlyElementsOf(optionIds);

            // Back to a draft, then published as saved: only the status changes.
            question.put("status", "DRAFT");
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
            mockMvc.perform(api.authPost(PATH + "/level-check-questions/" + id + "/publish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.audioText").value("你好"))
                    .andExpect(jsonPath("$.data.options[1].text").value("Hi"));
            mockMvc.perform(api.authPost(PATH + "/level-check-questions/" + id + "/publish", admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This question is already published."));
            question.put("status", "DRAFT");
            mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question))
                    .andExpect(status().isOk());
            cleanedUp = true;
        } finally {
            if (!cleanedUp) {
                question.put("status", "DRAFT");
                mockMvc.perform(api.jsonPut(PATH + "/level-check-questions/" + id, admin, question));
            }
        }
    }

    @Test
    void thePracticeLibraryListsFiltersAndShowsSets() throws Exception {
        String admin = api.adminToken();
        mockMvc.perform(api.authGet(LIBRARY, admin).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(greaterThanOrEqualTo(30)))
                .andExpect(jsonPath("$.data.items.length()").value(10));
        JsonNode listening = api.data(mockMvc.perform(api.authGet(LIBRARY, admin).param("type", "listening").param("size", "50"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(listening.path("items")).isNotEmpty();
        listening.path("items").forEach(row -> assertThat(row.path("type").asText()).isEqualTo("LISTENING"));
        mockMvc.perform(api.authGet(LIBRARY, admin).param("type", "quizzes"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.authGet(LIBRARY, admin).param("q", "campus words"))
                .andExpect(jsonPath("$.data.items[0].title").value("Campus Words"));
        mockMvc.perform(api.authGet(LIBRARY, admin).param("type", "poems"))
                .andExpect(status().isBadRequest());

        JsonNode seeded = api.data(mockMvc.perform(api.authGet(LIBRARY, admin).param("q", "At the Café")).andReturn())
                .path("items").get(0);
        long setId = seeded.path("id").asLong();
        mockMvc.perform(api.authGet(LIBRARY + "/listening/" + setId, admin))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].audioText").value("一杯茶"))
                .andExpect(jsonPath("$.data.items[0].correctIndex").value(0))
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.playedBy").isNumber())
                .andExpect(jsonPath("$.data.history[0].action").value("CREATED"));
    }

    @Test
    @Transactional
    void librarySearchPagesLiteralTitlesAcrossTypesAndSkipsUntitledLegacySets() throws Exception {
        String admin = api.adminToken();
        String marker = "Library " + uniqueSuffix();
        String keyword = marker + " shèng_4%";
        String cards = marker + " SHÈNG_4% cards";
        String listening = keyword + " listening";
        jdbc.update("""
                insert into flashcards (title, xp_reward, content_status, created_at, updated_at) values
                    (null, 0, 'DRAFT', now(), now()),
                    (?, 0, 'DRAFT', now() - interval 1 day, now() - interval 1 day),
                    (?, 0, 'DRAFT', now(), now()),
                    (?, 0, 'DRAFT', now(), now()),
                    (?, 0, 'DRAFT', now(), now())
                """, cards, marker + " sheng_4%", marker + " shèngX4%", marker + " shèng_4");
        jdbc.update("""
                insert into listening_exercises (title, xp_reward, content_status, created_at, updated_at)
                values (?, 0, 'DRAFT', now(), now())
                """, listening);

        mockMvc.perform(api.authGet(LIBRARY, admin).param("q", keyword).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[*].title").value(contains(listening)))
                .andExpect(jsonPath("$.data.items[0].type").value("LISTENING"));
        mockMvc.perform(api.authGet(LIBRARY, admin).param("q", keyword).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title").value(contains(cards)))
                .andExpect(jsonPath("$.data.items[0].type").value("FLASHCARDS"));
        mockMvc.perform(api.authGet(LIBRARY, admin).param("q", keyword).param("size", "1").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalItems").value(2));
    }

    @Test
    void anIncompleteTypingDraftCanBeOpenedRepairedAndPublished() throws Exception {
        String admin = api.adminToken();
        String title = "Typing draft " + uniqueSuffix();
        jdbc.update("""
                insert into typing_exercises (title, word_payload, xp_reward, content_status, created_at, updated_at)
                values (?, '{"words":[{"meaning":"cat","pinyin":"māo"}]}', 2, 'DRAFT', now(), now())
                """, title);
        Long id = jdbc.queryForObject("select id from typing_exercises where title = ?", Long.class, title);
        String path = LIBRARY + "/typing/" + id;
        mockMvc.perform(api.authGet(path, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].meaning").value("cat"));
        mockMvc.perform(api.jsonPost(path + "/publish", admin, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every word needs Chinese, pinyin and a meaning."));
        mockMvc.perform(api.jsonPut(path, admin, Map.of("title", title, "items", List.of(Map.of(
                        "hanzi", "猫", "pinyin", "māo", "meaning", "cat", "distractors", "毛 帽")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        try {
            mockMvc.perform(api.jsonPost(path + "/publish", admin, Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        } finally {
            mockMvc.perform(api.authPost(path + "/archive", admin));
        }
    }

    @Test
    void newSetsStartAsDraftsAndMoveThroughPublishUnpublishArchiveRestore() throws Exception {
        String admin = api.adminToken();
        String student = api.registerStudent(uniqueEmail("library-student"));
        String learnerEmail = uniqueEmail("library-active-learner");
        String title = "Colours Heard " + uniqueSuffix();
        Map<String, Object> body = listeningSet(title);
        mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, Map.of("title", title, "items", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least one item to this set."));
        // The XP follows the rule (3 an answer), whatever the request says.
        Long id = api.id(mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.xpReward").value(3))
                .andExpect(jsonPath("$.data.createdBy").isNotEmpty())
                .andReturn());
        // The library lists the newly created draft set.
        mockMvc.perform(api.authGet(LIBRARY, admin).param("q", title))
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
        String studentPath = "/api/v1/students/practice/listening/sets/" + id;
        mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());
        // A learner who earned XP lately hears of the set once it is published, once.
        api.registerStudentAccount(learnerEmail);
        earnXp(learnerEmail, LocalDateTime.now());

        boolean cleanedUp = false;
        try {
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/archive", admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only a published set can be archived."));
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/publish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
            mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isOk());

            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/unpublish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/archive", admin))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());

            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/publish", admin))
                    .andExpect(status().isOk());
            assertThat(notificationsOf(learnerEmail)).singleElement().satisfies(notification -> {
                assertThat(notification.getTitle()).isEqualTo("New in Practice");
                assertThat(notification.getMessage()).isEqualTo("A new listening set is ready: “" + title + "”.");
                assertThat(notification.getTargetPath()).isEqualTo("/student/practice/listening?set=" + id);
            });
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/archive", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
            mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());
            // A restored set is a draft again (A4.19): an admin publishes it once it is checked.
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/restore", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.history[0].action").value("RESTORED"))
                    .andExpect(jsonPath("$.data.history.length()").value(6));
            mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());

            // An edit of the draft is not audited: its History stays as it was.
            Map<String, Object> edited = new HashMap<>(body);
            edited.put("title", title + " (edited)");
            mockMvc.perform(api.jsonPut(LIBRARY + "/listening/" + id, admin, edited))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value(title + " (edited)"))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.history[0].action").value("RESTORED"))
                    .andExpect(jsonPath("$.data.history.length()").value(6));

            // Undo of Restore returns it to the archive, keeping any edits made after the restore.
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/archive", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                    .andExpect(jsonPath("$.data.title").value(title + " (edited)"))
                    .andExpect(jsonPath("$.data.history[0].action").value("ARCHIVED"));
            // The Undo of that archiving puts back the restored draft it was, unlike Restore from a published set.
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/unarchive", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.history[0].action").value("RESTORED"));
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/unarchive", admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only an archived set can be restored."));
            cleanedUp = true;
        } finally {
            if (!cleanedUp) {
                mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/unpublish", admin));
            }
        }
    }

    @Test
    void overlappingPublishesOfOneDraftAreAuditedOnce() throws Exception {
        String token = api.adminToken();
        long id = api.id(mockMvc.perform(api.jsonPost(LIBRARY + "/listening", token,
                        listeningSet("Publish together " + uniqueSuffix())))
                .andExpect(status().isOk()).andReturn());
        var admin = principalFor(ADMIN_EMAIL);
        try {
            assertSecondPublishRejected("listening_exercises",
                    () -> assertThat(library.publish(admin, PracticeType.LISTENING, id).status()).isEqualTo(ContentStatus.PUBLISHED),
                    LIBRARY + "/listening/" + id + "/publish", token, "This set is already published.");
            assertThat(auditEntriesAbout(PracticeType.LISTENING.referenceType(), id))
                    .extracting(AuditLog::getActionType).containsExactly("PRACTICE_SET_PUBLISH", "PRACTICE_SET_CREATE");
        } finally {
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/archive", token));
        }
    }

    private void assertSecondPublishRejected(String table, Runnable firstPublish, String endpoint, String token,
            String expectedMessage) throws Exception {
        withRowLock(jdbc, table, readCommitted(transactionManager), firstPublish, () -> mockMvc.perform(api.authPost(endpoint, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage)));
    }

    /**
     * The Undo of Archive puts a published set back as it was, published (A4.14), where Restore from the list makes it a
     * draft (A4.19). Edits of a published set are audited and show in its History; edits of a draft are not.
     */
    @Test
    void anUndoneArchiveIsPublishedAgainAndOnlyLiveEditsAreAudited() throws Exception {
        String admin = api.adminToken();
        String title = "Numbers Heard " + uniqueSuffix();
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "hskLevel", 1, "items", List.of(
                Map.of("prompt", "Which number?", "audioText", "三",
                        "options", List.of(Map.of("text", "three"), Map.of("text", "four")), "correctIndex", 0))));
        Long id = api.id(mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, body)).andExpect(status().isOk()).andReturn());
        String set = LIBRARY + "/listening/" + id;
        String student = api.registerStudent(uniqueEmail("library-unarchive-student"));
        String studentPath = "/api/v1/students/practice/listening/sets/" + id;

        body.put("title", title + " (draft edit)");
        mockMvc.perform(api.jsonPut(set, admin, body))
                .andExpect(jsonPath("$.data.history.length()").value(1))
                .andExpect(jsonPath("$.data.history[0].action").value("CREATED"));
        mockMvc.perform(api.authPost(set + "/publish", admin)).andExpect(status().isOk());
        try {
            body.put("title", title + " (live edit)");
            mockMvc.perform(api.jsonPut(set, admin, body))
                    .andExpect(jsonPath("$.data.history[0].action").value("UPDATED"))
                    .andExpect(jsonPath("$.data.history.length()").value(3));
            mockMvc.perform(api.authPost(set + "/archive", admin))
                    .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
            mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isNotFound());

            mockMvc.perform(api.authPost(set + "/unarchive", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.history[0].action").value("PUBLISHED"));
            mockMvc.perform(api.authGet(studentPath, student)).andExpect(status().isOk());
            mockMvc.perform(api.authPost(set + "/unarchive", admin)).andExpect(status().isBadRequest());
        } finally {
            mockMvc.perform(api.authPost(set + "/archive", admin));
        }

        // An unaudited legacy archived set cannot skip restore/review via /unarchive.
        String legacyTitle = "Legacy archived " + uniqueSuffix();
        jdbc.update("""
                insert into listening_exercises (title, question_payload, xp_reward, content_status, created_at, updated_at)
                values (?, '{"questions":[]}', 5, 'ARCHIVED', now(), now())
                """, legacyTitle);
        Long legacyId = jdbc.queryForObject("select id from listening_exercises where title = ?", Long.class, legacyTitle);
        try {
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + legacyId + "/unarchive", admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Restore this set instead of publishing it."));
        } finally {
            jdbc.update("delete from listening_exercises where id = ?", legacyId);
        }
    }

    /** "New in Practice" goes to the learners who earned XP in the last 30 days, today included. */
    @Test
    void newInPracticeGoesToTheLearnersWhoEarnedXpInTheLast30Days() throws Exception {
        String admin = api.adminToken();
        LocalDateTime firstDay = clock.startOf(clock.today().minusDays(29));
        String recent = uniqueEmail("library-news-recent");
        api.registerStudentAccount(recent);
        earnXp(recent, firstDay.plusHours(1));
        String lapsed = uniqueEmail("library-news-lapsed");
        api.registerStudentAccount(lapsed);
        earnXp(lapsed, firstDay.minusHours(1));
        Long id = api.id(mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin,
                        listeningSet("Colours Again " + uniqueSuffix())))
                .andExpect(status().isOk())
                .andReturn());

        String setPath = LIBRARY + "/listening/" + id;
        try {
            mockMvc.perform(api.authPost(setPath + "/publish", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            assertThat(notificationsOf(recent)).hasSize(1);
            assertThat(notificationsOf(lapsed)).isEmpty();

            // Even if all original deliveries are gone, restoring publication does not announce an old set as new.
            jdbc.update("delete from notifications where notification_type = 'NEW_PRACTICE_SET' and target_type = ? and target_id = ?",
                    PracticeType.LISTENING.referenceType(), id);
            mockMvc.perform(api.authPost(setPath + "/unpublish", admin)).andExpect(status().isOk());
            mockMvc.perform(api.authPost(setPath + "/publish", admin)).andExpect(status().isOk());
            assertThat(notificationsOf(recent)).isEmpty();
        } finally {
            mockMvc.perform(api.authPost(setPath + "/archive", admin));
        }
    }

    /** A listening set of one item, as the admin's form sends it. */
    private static Map<String, Object> listeningSet(String title) {
        return Map.of(
                "title", title,
                "stage", "GUIDED",
                "hskLevel", 1,
                "xpReward", 99,
                "items", List.of(Map.of(
                        "prompt", "Which colour did you hear?",
                        "audioText", "红色",
                        "options", List.of(Map.of("text", "red"), Map.of("text", "blue")),
                        "correctIndex", 0,
                        "explanation", "红 is red.")));
    }

    /** Some practice XP, earned at {@code at}. */
    private void earnXp(String email, LocalDateTime at) {
        PointsTransaction earned = new PointsTransaction();
        earned.setStudent(studentProfile(email));
        earned.setPointsDelta(10);
        earned.setReason("Practice");
        earned.setSourceType("TEST_PRACTICE");
        earned.setSourceId(1L);
        earned.setCreatedAt(at);
        pointsTransactionRepository.save(earned);
    }

    @Test
    void anEditKeepsTheItemsOfASetSoARunAndItsMistakesGoOn() throws Exception {
        String admin = api.adminToken();
        String student = api.registerStudent(uniqueEmail("library-edit-student"));
        String title = "Drinks Heard " + uniqueSuffix();
        List<Map<String, Object>> questions = new ArrayList<>(List.of(
                Map.of("prompt", "What does 茶 mean?", "audioText", "茶",
                        "options", List.of(Map.of("text", "tea"), Map.of("text", "rice")), "correctIndex", 0),
                Map.of("prompt", "What does 水 mean?", "audioText", "水",
                        "options", List.of(Map.of("text", "water"), Map.of("text", "milk")), "correctIndex", 0)));
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "hskLevel", 1, "items", questions));
        Long id = api.id(mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, body)).andExpect(status().isOk()).andReturn());
        mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/publish", admin)).andExpect(status().isOk());
        String studentPath = "/api/v1/students/practice/listening/sets/" + id;
        try {
            JsonNode before = api.data(mockMvc.perform(api.authGet(studentPath, student)).andReturn());
            String firstKey = before.path("items").get(0).path("key").asText();
            mockMvc.perform(api.jsonPost(studentPath + "/check", student, Map.of("itemKey", firstKey, "answer", "rice")))
                    .andExpect(jsonPath("$.data.correct").value(false));

            // A typo fixed while the student is playing: the run goes on, and a second wrong answer is the same mistake.
            questions.set(1, Map.of("prompt", "What does 水 (shuǐ) mean?", "audioText", "水",
                    "options", List.of(Map.of("text", "water"), Map.of("text", "milk")), "correctIndex", 0));
            body.put("title", title + " (fixed)");
            mockMvc.perform(api.jsonPut(LIBRARY + "/listening/" + id, admin, body)).andExpect(status().isOk());
            JsonNode after = api.data(mockMvc.perform(api.authGet(studentPath, student)).andReturn());
            assertThat(after.path("items").get(0).path("key").asText()).isEqualTo(firstKey);
            assertThat(after.path("items").get(1).path("key").asText())
                    .isEqualTo(before.path("items").get(1).path("key").asText());
            assertThat(after.path("items").get(1).path("prompt").asText()).isEqualTo("What does 水 (shuǐ) mean?");
            mockMvc.perform(api.jsonPost(studentPath + "/check", student, Map.of("itemKey", firstKey, "answer", "rice")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.correct").value(false));
            mockMvc.perform(api.authGet("/api/v1/students/mistakes", student).param("source", "practice"))
                    .andExpect(jsonPath("$.data.practice").value(1));

            // A shorter set drops its last items only.
            body.put("items", questions.subList(0, 1));
            mockMvc.perform(api.jsonPut(LIBRARY + "/listening/" + id, admin, body))
                    .andExpect(jsonPath("$.data.items.length()").value(1));
            mockMvc.perform(api.authGet(studentPath, student))
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].key").value(firstKey));
        } finally {
            mockMvc.perform(api.authPost(LIBRARY + "/listening/" + id + "/archive", admin));
        }
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every sentence needs at least 2 tiles and a meaning."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/tracing", admin, Map.of("title", "Write 人", "items", List.of(
                        Map.of("hanzi", "人")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter how many strokes the characters have."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/tracing", admin, Map.of("title", "Too many strokes", "strokeCount", 61,
                        "items", List.of(Map.of("hanzi", "人")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Strokes must be between 1 and 60."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, Map.of("title", "Hear it", "items", List.of(
                        Map.of("prompt", "Which?", "options", List.of(Map.of("text", "a"), Map.of("text", "b")),
                                "correctIndex", 0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every listening question needs the Chinese that is read aloud."));
        // Two answers that read the same would both be graded right.
        mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, Map.of("title", "Same answers", "items", List.of(
                        Map.of("prompt", "Which?", "audioText", "你好", "correctIndex", 1,
                                "options", List.of(Map.of("text", "Hello"), Map.of("text", " hello! ")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The answers of a question must all be different."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/listening", admin, Map.of("title", "Empty answer", "items", List.of(
                        Map.of("prompt", "Which?", "audioText", "你好", "correctIndex", 0,
                                "options", List.of(Map.of("text", "！？"), Map.of("text", "hello")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every question needs at least 2 answers with the right one ticked."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/tracing", admin, Map.of("title", "Two rows", "strokeCount", 8,
                        "items", List.of(Map.of("hanzi", "中"), Map.of("hanzi", "文")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Put the characters of a tracing set in one row."));
        String tenCharacters = "𠀀　".repeat(9) + "中";
        mockMvc.perform(api.jsonPost(LIBRARY + "/tracing", admin, Map.of("title", "Long word", "strokeCount", 10,
                        "items", List.of(Map.of("hanzi", tenCharacters + "文")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A tracing set has at most 10 characters."));
        mockMvc.perform(api.jsonPost(LIBRARY + "/tracing", admin, Map.of("title", "Ten characters " + uniqueSuffix(), "strokeCount", 10,
                        "items", List.of(Map.of("hanzi", tenCharacters)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].hanzi").value(tenCharacters));
        // A word's strokes per character must add up to its strokes.
        mockMvc.perform(api.jsonPost(LIBRARY + "/tracing", admin, Map.of("title", "Write 中文", "strokeCount", 8,
                        "characterStrokes", List.of(4, 3), "items", List.of(Map.of("hanzi", "中文")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Give the strokes of each character, adding up to the total."));
        // A look-alike given twice is one look-alike.
        mockMvc.perform(api.jsonPost(LIBRARY + "/typing", admin, Map.of("title", "Twice", "items", List.of(
                        Map.of("hanzi", "谢谢", "pinyin", "xièxie", "meaning", "Thank you", "distractors", "写写 写写")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every word needs at least 2 look-alike words that are not the answer."));
    }

    @Test
    void onlyAdminsManageLearningContent() throws Exception {
        String student = api.registerStudent(uniqueEmail("library-other-student"));
        mockMvc.perform(api.authGet(LIBRARY, student)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(PATH, student)).andExpect(status().isForbidden());
        mockMvc.perform(api.jsonPost(LIBRARY + "/flashcards", student, Map.of("title", "Mine"))).andExpect(status().isForbidden());
    }
}
