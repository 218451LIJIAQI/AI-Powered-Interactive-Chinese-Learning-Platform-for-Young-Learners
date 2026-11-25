package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * Answers given with how sure the student is (and what that does to Mistakes and the week's counts), hints by level
 * and by the student's setting, and pinyin typing that only offers words for a complete spelling.
 */
class ConfidenceHintsTypingIntegrationTests extends ApiIntegrationTest {

    private static final String LISTENING = "/api/v1/students/practice/listening/sets/";
    private static final String TYPING = "/api/v1/students/practice/typing/sets/";

    @Test
    void studentsCanTypeTheLongestSpellingAnAuthorCanPublish() throws Exception {
        String student = api.registerStudent(uniqueEmail("long-spelling"));
        String admin = api.adminToken();
        String library = "/api/v1/admin/practice-sets/typing";
        String hanzi = "中文".repeat(25);
        String pinyin = "zhongwen".repeat(25);
        var word = Map.of("hanzi", hanzi, "pinyin", pinyin, "meaning", "Chinese repeated", "distractors", "中闻 众文");
        var exercise = Map.of("title", "Long spelling " + uniqueSuffix(), "hskLevel", 1, "items", List.of(word));
        long id = api.id(mockMvc.perform(api.jsonPost(library, admin, exercise))
                .andExpect(status().isOk()).andReturn());
        try {
            mockMvc.perform(api.authPost(library + "/" + id + "/publish", admin)).andExpect(status().isOk());
            JsonNode set = api.data(mockMvc.perform(api.authGet(TYPING + id, student)).andExpect(status().isOk()).andReturn());
            String key = set.path("items").get(0).path("key").asText();
            String candidates = TYPING + id + "/candidates";
            JsonNode response = api.data(mockMvc.perform(api.jsonPost(candidates, student, Map.of("itemKey", key, "pinyin", pinyin)))
                    .andExpect(status().isOk()).andReturn());
            assertThat(response.path("words")).extracting(JsonNode::asText).containsExactlyInAnyOrder(hanzi, "中闻", "众文");
            mockMvc.perform(api.jsonPost(candidates, student, Map.of("itemKey", key, "pinyin", pinyin + "a")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.pinyin[0]").value("Pinyin must not exceed 200 characters."));
        } finally {
            jdbc.update("delete from typing_exercises where id = ?", id);
        }
    }

    @Test
    void aWrongAnswerGivenAsSureLeadsTheMistakesAndTheWeekCountsHowSureAnswersWere() throws Exception {
        String token = api.registerStudent(uniqueEmail("confidence"));
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        List<PracticeCatalog.Item> items = practiceCatalog.content(PracticeType.LISTENING, set).items();
        String path = LISTENING + set.getId() + "/check";
        JsonNode run = api.data(mockMvc.perform(api.authGet(LISTENING + set.getId(), token)).andReturn());

        mockMvc.perform(api.jsonPost(path, token, answer(items.get(0), wrongOption(items.get(0)), "GUESS")))
                .andExpect(jsonPath("$.data.correct").value(false));
        mockMvc.perform(api.jsonPost(path, token, answer(items.get(1), wrongOption(items.get(1)), "SURE")))
                .andExpect(jsonPath("$.data.correct").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/progress/answers", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hintsUsed").value(0))
                .andExpect(jsonPath("$.data.mostHintsIn").doesNotExist());
        mockMvc.perform(api.jsonPost(path, token, answer(items.get(2), items.get(2).gradable().answer(), "GUESS")))
                .andExpect(jsonPath("$.data.correct").value(true));
        // Client-sent hintSteps are ignored: only hints recorded by the server demote a sure answer to a guess...
        Map<String, Object> spoofed = new HashMap<>(answer(items.get(3), items.get(3).gradable().answer(), "SURE"));
        spoofed.put("hintSteps", 2);
        mockMvc.perform(api.jsonPost(path, token, spoofed))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.effectiveConfidence").value("SURE"));
        // ...and after a hint it gave, the answer counts as a guess, whatever was sent.
        mockMvc.perform(api.jsonPost(LISTENING + set.getId() + "/hint", token, hintRequest(items.get(4), run)))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(path, token, answer(items.get(4), items.get(4).gradable().answer(), "SURE")))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.effectiveConfidence").value("GUESS"));

        // The sure mistake leads in both lists. In review it takes priority over the older guess.
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.all").value(2))
                .andExpect(jsonPath("$.data.page.items[0].sureWrong").value(true))
                .andExpect(jsonPath("$.data.page.items[0].prompt").value(items.get(1).data().prompt()))
                .andExpect(jsonPath("$.data.page.items[1].sureWrong").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(jsonPath("$.data.questions[0].prompt").value(items.get(1).data().prompt()));

        mockMvc.perform(api.authGet("/api/v1/students/progress/answers", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sureAndRight").value(1))
                .andExpect(jsonPath("$.data.luckyGuesses").value(2))
                .andExpect(jsonPath("$.data.sureButWrong").value(1))
                .andExpect(jsonPath("$.data.wrongGuesses").value(1))
                .andExpect(jsonPath("$.data.hintsUsed").value(1))
                .andExpect(jsonPath("$.data.mostHintsIn").value("LISTENING"));

        // Tracing hints (which never call /check) are included in weekly hintsUsed and mostHintsIn.
        String tracingToken = api.registerStudent(uniqueEmail("tracing-insights"));
        PracticeActivityContent tracing = practiceSet(PracticeType.TRACING, "Write 中文");
        PracticeCatalog.Item tracingItem = practiceCatalog.content(PracticeType.TRACING, tracing).items().getFirst();
        String tracingPath = "/api/v1/students/practice/tracing/sets/" + tracing.getId();
        JsonNode tracingRun = api.data(mockMvc.perform(api.authGet(tracingPath, tracingToken)).andReturn());
        mockMvc.perform(api.jsonPost(tracingPath + "/hint", tracingToken, hintRequest(tracingItem, tracingRun)))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet("/api/v1/students/progress/answers", tracingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hintsUsed").value(1))
                .andExpect(jsonPath("$.data.mostHintsIn").value("TRACING"));
    }

    @Test
    void typingOffersWordsOnlyForTheWholeSpellingAndNeverMarksTheAnswer() throws Exception {
        String token = api.registerStudent(uniqueEmail("typing"));
        PracticeActivityContent set = practiceSet(PracticeType.TYPING, "Greetings");
        String path = TYPING + set.getId();

        JsonNode played = api.data(mockMvc.perform(api.authGet(path, token)).andExpect(status().isOk()).andReturn());
        JsonNode first = played.path("items").get(0);
        assertThat(first.path("meaning").asText()).isEqualTo("Thank you");
        assertThat(first.path("hanzi").isNull()).isTrue();
        assertThat(first.path("pinyin").isNull()).isTrue();
        assertThat(first.path("options").size()).isZero();
        String key = first.path("key").asText();

        JsonNode words = api.data(mockMvc.perform(api.jsonPost(path + "/candidates", token,
                Map.of("itemKey", key, "pinyin", "Xiè xie"))).andExpect(status().isOk()).andReturn());
        assertThat(words.path("words")).extracting(JsonNode::asText).hasSize(5).contains("谢谢", "写写", "歇歇");
        assertThat(words.toString()).doesNotContain("pinyin").doesNotContain("correct");

        mockMvc.perform(api.jsonPost(path + "/candidates", token, Map.of("itemKey", key, "pinyin", "xie")))
                .andExpect(jsonPath("$.data.words.length()").value(0));
        mockMvc.perform(api.jsonPost(path + "/check", token,
                        Map.of("itemKey", key, "answer", "写写", "confidence", "SURE")))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value("谢谢"))
                .andExpect(jsonPath("$.data.pinyin").value("xièxie"));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.page.items[0].sureWrong").value(true))
                .andExpect(jsonPath("$.data.page.items[0].meaning").value("Thank you"));
        // In the review the words are offered alike: none carries pinyin, so the answer is not the one that has it.
        JsonNode review = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token)).andReturn());
        JsonNode reviewed = review.path("questions").get(0);
        assertThat(reviewed.path("meaning").asText()).isEqualTo("Thank you");
        assertThat(reviewed.path("options").size()).isEqualTo(5);
        reviewed.path("options").forEach(option -> assertThat(option.hasNonNull("pinyin")).isFalse());

        PracticeActivityContent listening = practiceSet(PracticeType.LISTENING, "At the Café");
        mockMvc.perform(api.jsonPost(LISTENING + listening.getId() + "/candidates", token, Map.of("itemKey", "1", "pinyin", "ni")))
                .andExpect(status().isNotFound());
    }

    /** A hint is one step (A5.05); an HSK 1 student's says as much as the second rung of the item's ladder. */
    @Test
    void aHintIsOneStepAsFullAsTheLevelGivesAndStopsWhenTheStudentTurnsHintsOff() throws Exception {
        String token = api.registerStudent(uniqueEmail("hints"));
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        PracticeCatalog.Item item = practiceCatalog.content(PracticeType.LISTENING, set).items().get(0);
        String path = LISTENING + set.getId() + "/hint";
        String answer = item.gradable().answer();

        mockMvc.perform(api.authGet("/api/v1/students/hint-settings", token))
                .andExpect(jsonPath("$.data.mode").value("AUTOMATIC"))
                .andExpect(jsonPath("$.data.tier").value("HSK1"))
                .andExpect(jsonPath("$.data.delaySeconds").value(15))
                .andExpect(jsonPath("$.data.perSet").value(0));
        JsonNode run = api.data(mockMvc.perform(api.authGet(LISTENING + set.getId(), token)).andReturn());

        // A listening ladder leads with the sound to listen for, then rules an answer out: HSK 1 gets the second rung.
        JsonNode hint = api.data(mockMvc.perform(api.jsonPost(path, token, hintRequest(item, run)))
                .andExpect(status().isOk()).andReturn());
        assertThat(hint.path("kind").asText()).isEqualTo("RULE_OUT");
        assertThat(hint.path("ruledOut").size()).isEqualTo(1);
        assertThat(hint.path("ruledOut").get(0).asText()).isNotEqualTo(answer);

        // Asked again, the item gets the same hint.
        JsonNode again = api.data(mockMvc.perform(api.jsonPost(path, token, hintRequest(item, run)))
                .andExpect(status().isOk()).andReturn());
        assertThat(again).isEqualTo(hint);

        mockMvc.perform(api.jsonPut("/api/v1/students/hint-settings", token, Map.of("mode", "OFF")))
                .andExpect(jsonPath("$.data.mode").value("OFF"));
        mockMvc.perform(api.jsonPost(path, token, hintRequest(item, run)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hints are off in your settings."));
    }

    @Test
    void adminsSetTheHintRulePerLevelAndStudentsGetIt() throws Exception {
        String admin = api.adminToken();
        String student = api.registerStudent(uniqueEmail("hint-rules"));
        mockMvc.perform(api.authGet("/api/v1/admin/ai/hints", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.levels.length()").value(3));
        mockMvc.perform(api.authGet("/api/v1/admin/ai/hints", student)).andExpect(status().isForbidden());

        List<Map<String, Object>> levels = List.of(
                Map.of("tier", "HSK1", "delaySeconds", 20, "perSet", 2),
                Map.of("tier", "HSK2", "delaySeconds", 30, "perSet", 3),
                Map.of("tier", "HSK3", "delaySeconds", 45, "perSet", 1));
        try (HintRules rules = HintRules.saved(mockMvc, api)) {
            mockMvc.perform(api.jsonPut("/api/v1/admin/ai/hints", admin, Map.of("levels", levels)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.levels[0].delaySeconds").value(20));
            mockMvc.perform(api.authGet("/api/v1/students/hint-settings", student))
                    .andExpect(jsonPath("$.data.delaySeconds").value(20))
                    .andExpect(jsonPath("$.data.perSet").value(2));

            PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
            PracticeCatalog.Item item = practiceCatalog.content(PracticeType.LISTENING, set).items().get(0);
            JsonNode run = api.data(mockMvc.perform(api.authGet(LISTENING + set.getId(), student)).andReturn());
            mockMvc.perform(api.jsonPost(LISTENING + set.getId() + "/hint", student, hintRequest(item, run)))
                    .andExpect(status().isOk());

            mockMvc.perform(api.jsonPut("/api/v1/admin/ai/hints", admin, Map.of("levels", levels.subList(0, 2))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(api.jsonPut("/api/v1/admin/ai/hints", admin,
                            Map.of("levels", List.of(levels.get(0), levels.get(0), levels.get(2)))))
                    .andExpect(status().isBadRequest());
        }
    }

    /**
     * The set's hint limit counts the items the server gave a hint in the run, whether their answers were checked or
     * not; the run is the one the server started, and a check after a hint is a guess whatever the request says.
     */
    @Test
    void aRunOfASetGetsHintsForAsManyItemsAsItsLevelAllowsAndANewRunStartsAgain() throws Exception {
        String student = api.registerStudent(uniqueEmail("hint-budget"));
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "Asking the Way");
        List<PracticeCatalog.Item> items = practiceCatalog.content(PracticeType.LISTENING, set).items();
        String path = LISTENING + set.getId();
        try (HintRules rules = HintRules.saved(mockMvc, api)) {
            rules.setHsk1(2);
            JsonNode run = api.data(mockMvc.perform(api.authGet(path, student)).andReturn());
            assertThat(run.path("runStartedAt").asText()).isNotBlank();
            assertThat(run.path("runSignature").asText()).isNotBlank();
            JsonNode otherTab = api.data(mockMvc.perform(api.authGet(path, student)).andReturn());

            // Two items with a hint use the run's two hints, though no answer was checked.
            for (PracticeCatalog.Item item : items.subList(0, 2)) {
                mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(item, run)))
                        .andExpect(status().isOk());
            }
            Map<String, Object> thirdHint = hintRequest(items.get(2), run);
            mockMvc.perform(api.jsonPost(path + "/hint", student, thirdHint))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There are no hints left in this set."));
            // An item that already had its hint in this run gets it again when it asks again.
            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(items.get(1), run)))
                    .andExpect(status().isOk());
            // Checks change nothing about the run's hint count: the run still counts the two items.
            mockMvc.perform(api.jsonPost(path + "/check", student,
                            answer(items.get(1), items.get(1).gradable().answer(), "SURE")))
                    .andExpect(status().isOk());
            mockMvc.perform(api.jsonPost(path + "/hint", student, thirdHint))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There are no hints left in this set."));
            // The sure right answer after its hint was a guess.
            mockMvc.perform(api.authGet("/api/v1/students/progress/answers", student))
                    .andExpect(jsonPath("$.data.sureAndRight").value(0))
                    .andExpect(jsonPath("$.data.luckyGuesses").value(1))
                    .andExpect(jsonPath("$.data.hintsUsed").value(1));

            // A run starting now, or a request without the server's signature, gets no hint.
            Map<String, Object> moved = new HashMap<>(thirdHint);
            moved.put("runStartedAt", OffsetDateTime.now().toString());
            mockMvc.perform(api.jsonPost(path + "/hint", student, moved))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));
            mockMvc.perform(api.jsonPost(path + "/hint", student, Map.of("itemKey", items.get(2).key())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));
            // A run of another set is not a run of this one.
            PracticeActivityContent other = practiceSet(PracticeType.LISTENING, "At the Café");
            JsonNode otherRun = api.data(mockMvc.perform(api.authGet(LISTENING + other.getId(), student)).andReturn());
            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(items.get(2), otherRun)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));

            // Both tabs were already open when the first tab used its allowance.
            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(items.get(2), otherTab)))
                    .andExpect(status().isOk());
            // Lowering the allowance stops new hints but preserves an item the learner already asked about.
            rules.setHsk1(1);
            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(items.get(3), otherTab)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There are no hints left in this set."));
            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(items.get(1), run)))
                    .andExpect(status().isOk());
        }
    }

    /**
     * A Matching board is one question with one hint, the pair of one word: a client that asks for every word gets no
     * more pairs than the stock player shows.
     */
    @Test
    void aMatchingBoardGetsThePairOfOneWordOnly() throws Exception {
        String student = api.registerStudent(uniqueEmail("hint-matching"));
        PracticeActivityContent set = practiceSet(PracticeType.MATCHING, "Numbers Match");
        List<PracticeCatalog.Item> words = practiceCatalog.content(PracticeType.MATCHING, set).items();
        String path = "/api/v1/students/practice/matching/sets/" + set.getId();
        assertThat(words.size()).isGreaterThan(2);
        try (HintRules rules = HintRules.saved(mockMvc, api)) {
            rules.setHsk1(0);
            JsonNode run = api.data(mockMvc.perform(api.authGet(path, student)).andReturn());
            JsonNode otherTab = api.data(mockMvc.perform(api.authGet(path, student)).andReturn());
            JsonNode first = api.data(mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(words.get(0), run)))
                    .andExpect(status().isOk()).andReturn());
            assertThat(first.path("kind").asText()).isEqualTo("PAIR");
            assertThat(first.path("words").get(0).asText()).isEqualTo(words.get(0).data().hanzi());
            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(words.get(1), run)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There is no further hint for this item."));
            // The word already given is given again.
            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(words.get(0), run)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.kind").value("PAIR"))
                    .andExpect(jsonPath("$.data.words[0]").value(words.get(0).data().hanzi()));

            // The pair checked after its hint is an answer after a hint.
            mockMvc.perform(api.jsonPost(path + "/check", student,
                            Map.of("itemKey", words.get(0).key(), "answer", words.get(0).gradable().answer())))
                    .andExpect(jsonPath("$.data.correct").value(true));
            mockMvc.perform(api.authGet("/api/v1/students/progress/answers", student))
                    .andExpect(jsonPath("$.data.hintsUsed").value(1))
                    .andExpect(jsonPath("$.data.mostHintsIn").value("MATCHING"));

            mockMvc.perform(api.jsonPost(path + "/hint", student, hintRequest(words.get(2), otherTab)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.kind").value("PAIR"))
                    .andExpect(jsonPath("$.data.words[0]").value(words.get(2).data().hanzi()));
        }
    }

    /** A hint request for the run of a set as the server sent it. */
    private static Map<String, Object> hintRequest(PracticeCatalog.Item item, JsonNode set) {
        return Map.of("itemKey", item.key(),
                "runStartedAt", set.path("runStartedAt").asText(),
                "runSignature", set.path("runSignature").asText());
    }

    @Test
    void theSetListSaysWhichSetsWereStartedButNotDone() throws Exception {
        String token = api.registerStudent(uniqueEmail("started"));
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "Asking the Way");
        String path = LISTENING + set.getId();
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Answer or skip every item before you finish the set."));
        mockMvc.perform(api.authGet("/api/v1/students/practice/listening/sets", token))
                .andExpect(jsonPath("$.data.sets[?(@.id == " + set.getId() + ")].started").value(contains(false)));
        JsonNode opened = api.data(mockMvc.perform(api.authGet(path, token)).andReturn());
        List<Map<String, String>> skips = new ArrayList<>();
        opened.path("items").forEach(item -> skips.add(Map.of("itemKey", item.path("key").asText())));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", skips)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allCorrect").value(false));
        JsonNode sets = api.data(mockMvc.perform(api.authGet("/api/v1/students/practice/listening/sets", token)).andReturn());
        sets.path("sets").forEach(summary -> {
            boolean played = summary.path("id").asLong() == set.getId();
            assertThat(summary.path("started").asBoolean()).as(summary.path("title").asText()).isEqualTo(played);
            assertThat(summary.path("done").asBoolean()).isFalse();
        });
    }

    private static Map<String, Object> answer(PracticeCatalog.Item item, String answer, String confidence) {
        return Map.of("itemKey", item.key(), "answer", answer, "confidence", confidence);
    }

}
