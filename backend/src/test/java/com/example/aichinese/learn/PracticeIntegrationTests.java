package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.support.ApiIntegrationTest;

/** Practice sets: no answer keys before answering, server checks, first-time XP, the quiz streak bonus and mistakes. */
class PracticeIntegrationTests extends ApiIntegrationTest {

    @Autowired
    private PracticeCatalog catalog;

    @Test
    void eachTypeListsItsPublishedSetsWithDoneFlags() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-list"));
        for (String slug : List.of("flashcards", "quizzes", "listening", "tracing", "drag-drop", "matching", "story")) {
            JsonNode sets = api.data(mockMvc.perform(api.authGet("/api/v1/students/practice/" + slug + "/sets", token))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(sets.path("sets").size()).as(slug).isGreaterThanOrEqualTo(5);
            sets.path("sets").forEach(set -> {
                assertThat(set.path("done").asBoolean()).isFalse();
                assertThat(set.path("itemCount").asInt()).isPositive();
            });
        }
        mockMvc.perform(api.authGet("/api/v1/students/practice/flashcards/sets", token))
                .andExpect(jsonPath("$.data.sets[0].title").value("Campus Words"))
                .andExpect(jsonPath("$.data.sets[0].itemCount").value(6))
                .andExpect(jsonPath("$.data.sets[0].xpReward").value(10));
        mockMvc.perform(api.authGet("/api/v1/students/practice/poems/sets", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewingFlashcardsEarnsTheirXpOnce() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-cards"));
        PracticeActivityContent set = set(PracticeType.FLASHCARDS, "Campus Words");
        String path = "/api/v1/students/practice/flashcards/sets/" + set.getId();

        mockMvc.perform(api.authGet(path, token))
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].hanzi").value("图书馆"))
                .andExpect(jsonPath("$.data.items[0].meaning").value("library"))
                .andExpect(jsonPath("$.data.items[0].example").value("我在图书馆看书。"));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of()))
                .andExpect(jsonPath("$.data.allCorrect").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10))
                .andExpect(jsonPath("$.data.nextSet.title").value("Food and Drink"))
                .andExpect(jsonPath("$.data.xp.newBadges[0]").value("FIRST_STEPS"));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of()))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/practice/flashcards/sets", token))
                .andExpect(jsonPath("$.data.sets[0].done").value(true));
        mockMvc.perform(api.authGet("/api/v1/students/today", token))
                .andExpect(jsonPath("$.data.setsDoneToday").value(2))
                .andExpect(jsonPath("$.data.todayXp").value(10));
    }

    @Test
    void quizzesCheckAnswersOnTheServerAndPayTheInARowBonusOnce() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-quiz"));
        PracticeActivityContent set = set(PracticeType.QUIZZES, "Greetings Quiz");
        String path = "/api/v1/students/practice/quizzes/sets/" + set.getId();
        List<PracticeCatalog.Item> items = catalog.content(PracticeType.QUIZZES, set).items();

        JsonNode quiz = api.data(mockMvc.perform(api.authGet(path, token)).andExpect(status().isOk()).andReturn());
        assertThat(quiz.toString()).doesNotContain("correctIndex").doesNotContain("explanation");
        assertThat(quiz.path("items").get(0).path("options").size()).isEqualTo(4);

        PracticeCatalog.Item first = items.get(0);
        mockMvc.perform(api.jsonPost(path + "/check", token, Map.of("itemKey", first.key(), "answer", "Sorry")))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value("Hello"))
                .andExpect(jsonPath("$.data.explanation").isNotEmpty());
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token).param("source", "practice"))
                .andExpect(jsonPath("$.data.practice").value(1))
                .andExpect(jsonPath("$.data.page.items[0].prompt").value("What does 你好 mean?"));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(jsonPath("$.data.context[0].sourceTitle").value("Greetings Quiz"));

        // Wrong first, then five right in a row: no set XP (not every item right) but the in-a-row bonus.
        List<Map<String, String>> answers = new ArrayList<>();
        answers.add(Map.of("itemKey", first.key(), "answer", "Sorry"));
        items.subList(1, items.size()).forEach(item ->
                answers.add(Map.of("itemKey", item.key(), "answer", item.gradable().answer())));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", answers)))
                .andExpect(jsonPath("$.data.correctCount").value(5))
                .andExpect(jsonPath("$.data.allCorrect").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(5))
                .andExpect(jsonPath("$.data.xp.newBadges[0]").value("QUIZ_STAR"))
                .andExpect(jsonPath("$.data.results[0].correct").value(false));

        answers.set(0, Map.of("itemKey", first.key(), "answer", "Hello"));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", answers)))
                .andExpect(jsonPath("$.data.allCorrect").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(15));
    }

    @Test
    void sentencesAreBuiltFromTilesAndMatchingPairsAreGraded() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-build"));
        PracticeActivityContent sentences = set(PracticeType.DRAG_DROP, "Places on Campus");
        String path = "/api/v1/students/practice/drag-drop/sets/" + sentences.getId();
        JsonNode built = api.data(mockMvc.perform(api.authGet(path, token)).andReturn());
        JsonNode firstSentence = built.path("items").get(0);
        assertThat(firstSentence.path("meaning").asText()).isEqualTo("I go to the library.");
        assertThat(firstSentence.path("hanzi").isNull()).isTrue();
        assertThat(firstSentence.path("tiles").size()).isEqualTo(5);

        mockMvc.perform(api.jsonPost(path + "/check", token,
                        Map.of("itemKey", firstSentence.path("key").asText(), "answer", "我 去 图书馆")))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.correctAnswer").value("我去图书馆"));
        mockMvc.perform(api.jsonPost(path + "/check", token,
                        Map.of("itemKey", firstSentence.path("key").asText(), "answer", "图书馆 去 我")))
                .andExpect(jsonPath("$.data.correct").value(false));

        PracticeActivityContent pairs = set(PracticeType.MATCHING, "Numbers Match");
        String pairsPath = "/api/v1/students/practice/matching/sets/" + pairs.getId();
        JsonNode game = api.data(mockMvc.perform(api.authGet(pairsPath, token)).andReturn());
        assertThat(game.path("matchingChoices").size()).isEqualTo(4);
        List<Map<String, String>> matches = new ArrayList<>();
        catalog.content(PracticeType.MATCHING, pairs).items().forEach(item ->
                matches.add(Map.of("itemKey", item.key(), "answer", item.data().meaning())));
        mockMvc.perform(api.jsonPost(pairsPath + "/complete", token, Map.of("answers", matches)))
                .andExpect(jsonPath("$.data.allCorrect").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(15));
    }

    @Test
    void tracingNeedsEveryStroke() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-trace"));
        PracticeActivityContent set = set(PracticeType.TRACING, "Write 学");
        String path = "/api/v1/students/practice/tracing/sets/" + set.getId();
        mockMvc.perform(api.authGet(path, token))
                .andExpect(jsonPath("$.data.strokeCount").value(8))
                .andExpect(jsonPath("$.data.items[0].hanzi").value("学"));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("strokeCount", 3)))
                .andExpect(jsonPath("$.data.allCorrect").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("strokeCount", 8)))
                .andExpect(jsonPath("$.data.allCorrect").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10));
        mockMvc.perform(api.jsonPost(path + "/check", token, Map.of("itemKey", "0", "answer", "学")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void draftSetsAndOtherRolesAreKeptOut() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-draft"));
        PracticeActivityContent set = set(PracticeType.STORY, "My Weekend");
        set.setContentStatus(ContentStatus.DRAFT);
        catalog.save(PracticeType.STORY, set);
        try {
            mockMvc.perform(api.authGet("/api/v1/students/practice/story/sets/" + set.getId(), token))
                    .andExpect(status().isNotFound());
            JsonNode stories = api.data(mockMvc.perform(api.authGet("/api/v1/students/practice/story/sets", token))
                    .andReturn());
            assertThat(stories.findValuesAsText("title")).doesNotContain("My Weekend");
        } finally {
            set.setContentStatus(ContentStatus.PUBLISHED);
            catalog.save(PracticeType.STORY, set);
        }
        String teacherToken = api.approvedTeacher(uniqueEmail("practice-teacher"));
        mockMvc.perform(api.authGet("/api/v1/students/practice/story/sets", teacherToken))
                .andExpect(status().isForbidden());
    }

    private PracticeActivityContent set(PracticeType type, String title) {
        return catalog.sets(type, null).stream()
                .filter(candidate -> title.equals(candidate.getTitle()))
                .findFirst()
                .orElseThrow();
    }
}
