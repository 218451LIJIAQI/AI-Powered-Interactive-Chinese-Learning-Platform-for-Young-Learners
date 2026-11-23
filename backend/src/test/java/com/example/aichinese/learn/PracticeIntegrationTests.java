package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.support.ApiIntegrationTest;

/** Practice sets: no answer keys before answering, server checks, first-time XP, flashcard ratings and mistakes. */
class PracticeIntegrationTests extends ApiIntegrationTest {

    @Autowired
    private TracingExerciseRepository tracingRepository;

    @Test
    void eachTypeListsItsPublishedSetsWithDoneFlags() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-list"));
        for (String slug : List.of("flashcards", "listening", "tracing", "drag-drop", "matching", "typing")) {
            JsonNode sets = api.data(mockMvc.perform(api.authGet("/api/v1/students/practice/" + slug + "/sets", token))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(sets.path("sets").size()).as(slug).isGreaterThanOrEqualTo(5);
            sets.path("sets").forEach(set -> {
                assertThat(set.path("done").asBoolean()).isFalse();
                assertThat(set.path("started").asBoolean()).isFalse();
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
        PracticeActivityContent set = practiceSet(PracticeType.FLASHCARDS, "Campus Words");
        String path = "/api/v1/students/practice/flashcards/sets/" + set.getId();

        mockMvc.perform(api.authGet(path, token))
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].hanzi").value("图书馆"))
                .andExpect(jsonPath("$.data.items[0].meaning").value("library"))
                .andExpect(jsonPath("$.data.items[0].example").value("我在图书馆看书。"));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", gotItRatings(set))))
                .andExpect(jsonPath("$.data.allCorrect").value(true))
                .andExpect(jsonPath("$.data.correctCount").value(6))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10))
                .andExpect(jsonPath("$.data.xp.rankUp").doesNotExist())
                .andExpect(jsonPath("$.data.nextSet.title").value("Food and Drink"))
                .andExpect(jsonPath("$.data.xp.newBadges[0]").value("FIRST_STEPS"));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", gotItRatings(set))))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/practice/flashcards/sets", token))
                .andExpect(jsonPath("$.data.sets[0].done").value(true));
        mockMvc.perform(api.authGet("/api/v1/students/today", token))
                .andExpect(jsonPath("$.data.todayXp").value(10));
    }

    /**
     * A card rated "Still learning" becomes a Practice mistake, asked in the review as what the word means; the first
     * review pays the set's XP whatever the ratings. In the review a skipped card stays waiting as it was, a right
     * answer clears it.
     */
    @Test
    void cardsStillBeingLearnedBecomeMistakesReviewedAsAChoice() throws Exception {
        String email = uniqueEmail("practice-still-learning");
        String token = api.registerStudent(email);
        PracticeActivityContent set = practiceSet(PracticeType.FLASHCARDS, "Greetings");
        String path = "/api/v1/students/practice/flashcards/sets/" + set.getId();
        List<PracticeCatalog.Item> cards = practiceCatalog.content(PracticeType.FLASHCARDS, set).items();
        List<Map<String, String>> answers = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            answers.add(Map.of("itemKey", cards.get(i).key(), "answer", i < 2 ? "STILL_LEARNING" : "GOT_IT"));
        }

        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", answers.subList(1, answers.size()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rate each card Got it or Still learning."));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", List.of(
                        Map.of("itemKey", cards.get(0).key(), "answer", "MAYBE")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rate each card Got it or Still learning."));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctCount").value(4))
                .andExpect(jsonPath("$.data.allCorrect").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10));

        JsonNode list = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes", token).param("source", "practice"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(list.path("practice").asInt()).isEqualTo(2);
        assertThat(list.path("page").path("items")).allSatisfy(row -> {
            assertThat(row.path("stillLearning").asBoolean()).isTrue();
            assertThat(row.path("type").asText()).isEqualTo("MEANING");
            assertThat(row.path("yourAnswer").isNull()).isTrue();
        });
        assertThat(list.path("page").path("items").findValuesAsText("hanzi")).containsExactlyInAnyOrder("你好", "谢谢");

        JsonNode review = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(review.path("context")).allSatisfy(context -> {
            assertThat(context.path("stillLearning").asBoolean()).isTrue();
            assertThat(context.path("sourceTitle").asText()).isEqualTo("Greetings");
        });
        JsonNode hello = review.path("questions").get(0);
        assertThat(hello.path("options").size()).isEqualTo(4);
        assertThat(hello.path("options").findValuesAsText("text")).contains(
                hello.path("hanzi").asText().equals("你好") ? "hello" : "thank you");

        // Skip the first, answer the second right: one cleared, one still waiting, still a card being learned.
        long skipped = hello.path("id").asLong();
        JsonNode second = review.path("questions").get(1);
        String right = second.path("hanzi").asText().equals("你好") ? "hello" : "thank you";
        Map<String, Object> skip = new HashMap<>();
        skip.put("questionId", skipped);
        skip.put("answer", null);
        var reviewAnswers = List.of(skip, Map.of("questionId", second.path("id").asLong(), "answer", right));
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", token, Map.of("answers", reviewAnswers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(1))
                .andExpect(jsonPath("$.data.left").value(1));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.page.items[0].id").value(skipped))
                .andExpect(jsonPath("$.data.page.items[0].stillLearning").value(true));
    }

    @Test
    void theNextSetOfferedIsOneNotDoneYetInTheOrderOfTheList() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-next"));
        // The seeded flashcards in list order: the HSK 1 sets, then Travel Words (HSK 2).
        JsonNode sets = api.data(mockMvc.perform(api.authGet("/api/v1/students/practice/flashcards/sets", token))
                .andReturn()).path("sets");
        assertThat(sets).extracting(set -> set.path("title").asText())
                .containsExactly("Campus Words", "Food and Drink", "Numbers and Prices", "Family", "Greetings", "Travel Words");

        assertThat(finishCards(token, "Food and Drink")).isEqualTo("Numbers and Prices");
        // Food and Drink is done: the next set offered after Campus Words skips it.
        assertThat(finishCards(token, "Campus Words")).isEqualTo("Numbers and Prices");
        // After the last set of the list, the first one not done yet.
        assertThat(finishCards(token, "Travel Words")).isEqualTo("Numbers and Prices");
        assertThat(finishCards(token, "Numbers and Prices")).isEqualTo("Family");
        assertThat(finishCards(token, "Family")).isEqualTo("Greetings");
        // Every set done: the set after this one, and none after the last.
        assertThat(finishCards(token, "Greetings")).isEqualTo("Travel Words");
        assertThat(finishCards(token, "Travel Words")).isNull();
    }

    /** Reviews the flashcard set with this title, every card "Got it", and returns the next set offered, or null. */
    private String finishCards(String token, String title) throws Exception {
        PracticeActivityContent set = practiceSet(PracticeType.FLASHCARDS, title);
        JsonNode result = api.data(mockMvc.perform(api.jsonPost(
                        "/api/v1/students/practice/flashcards/sets/" + set.getId() + "/complete", token,
                        Map.of("answers", gotItRatings(set))))
                .andExpect(status().isOk())
                .andReturn());
        return result.path("nextSet").isNull() ? null : result.path("nextSet").path("title").asText();
    }

    @Test
    void listeningChecksAnswersOnTheServerAndAwardsXpWhenAllAreCorrect() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-listening"));
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        String setsPath = "/api/v1/students/practice/listening/sets";
        String path = setsPath + "/" + set.getId();
        String summary = "$.data.sets[?(@.id == " + set.getId() + ")]";
        List<PracticeCatalog.Item> items = practiceCatalog.content(PracticeType.LISTENING, set).items();

        JsonNode listening = api.data(mockMvc.perform(api.authGet(path, token)).andExpect(status().isOk()).andReturn());
        assertThat(listening.toString()).doesNotContain("correctIndex").doesNotContain("explanation")
                .doesNotContain("streakBonusEarned");
        assertThat(listening.path("items").get(0).path("options").size()).isEqualTo(4);
        // 3 XP an answer, 5 answers.
        assertThat(listening.path("xpReward").asInt()).isEqualTo(15);

        PracticeCatalog.Item first = items.get(0);
        mockMvc.perform(api.jsonPost(path + "/check", token, Map.of("itemKey", first.key(), "answer", "一碗米饭")))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value("一杯茶"))
                .andExpect(jsonPath("$.data.explanation").isNotEmpty());
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token).param("source", "practice"))
                .andExpect(jsonPath("$.data.practice").value(1))
                .andExpect(jsonPath("$.data.page.items[0].prompt").value("Which phrase did you hear?"))
                .andExpect(jsonPath("$.data.page.items[0].stillLearning").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(jsonPath("$.data.context[0].sourceTitle").value("At the Café"))
                .andExpect(jsonPath("$.data.context[0].stillLearning").value(false));

        // Wrong first, then four right: no set XP (not every item right).
        List<Map<String, String>> answers = new ArrayList<>();
        answers.add(Map.of("itemKey", first.key(), "answer", "一碗米饭"));
        items.subList(1, items.size()).forEach(item ->
                answers.add(Map.of("itemKey", item.key(), "answer", item.gradable().answer())));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", answers)))
                .andExpect(jsonPath("$.data.correctCount").value(4))
                .andExpect(jsonPath("$.data.allCorrect").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
        mockMvc.perform(api.authGet(path, token)).andExpect(jsonPath("$.data.done").value(false));
        mockMvc.perform(api.authGet(setsPath, token))
                .andExpect(jsonPath(summary + ".done").value(false))
                .andExpect(jsonPath(summary + ".started").value(true));

        answers.set(0, Map.of("itemKey", first.key(), "answer", "一杯茶"));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", answers)))
                .andExpect(jsonPath("$.data.allCorrect").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(15));
        mockMvc.perform(api.authGet(setsPath, token))
                .andExpect(jsonPath(summary + ".done").value(true))
                .andExpect(jsonPath(summary + ".started").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/practice/quizzes/sets", token))
                .andExpect(status().isNotFound());
    }

    /** Passing 100 XP opens Rank 2: the result says so (the rank-up dialog) and Progress gets a notification. */
    @Test
    void aResultThatPassesARankThresholdSaysWhichRank() throws Exception {
        String email = uniqueEmail("practice-rank");
        String token = api.registerStudent(email);
        var student = studentProfile(email);
        student.setTotalPoints(95);
        studentProfileRepository.save(student);
        PracticeActivityContent set = practiceSet(PracticeType.FLASHCARDS, "Campus Words");
        mockMvc.perform(api.jsonPost("/api/v1/students/practice/flashcards/sets/" + set.getId() + "/complete", token,
                        Map.of("answers", gotItRatings(set))))
                .andExpect(jsonPath("$.data.xp.rankUp.rank").value(2))
                .andExpect(jsonPath("$.data.xp.rankUp.rankXp").value(100))
                .andExpect(jsonPath("$.data.xp.rankUp.nextRankXp").value(200));
        assertThat(notificationsOf(email)).anySatisfy(notification -> {
            assertThat(notification.getTitle()).isEqualTo("You reached Rank 2");
            assertThat(notification.getMessage()).isEqualTo("You're now Rank 2 · Explorer. Rank 3 is next.");
            assertThat(notification.getTargetPath()).isEqualTo("/student/progress");
        });
    }

    @Test
    void skippedFirstAnswersCannotBeReplacedByDuplicateCorrectAnswersInOneRun() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-first-answer"));
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        String path = "/api/v1/students/practice/listening/sets/" + set.getId();
        List<Map<String, String>> answers = new ArrayList<>();
        for (PracticeCatalog.Item item : practiceCatalog.content(PracticeType.LISTENING, set).items()) {
            answers.add(Map.of("itemKey", item.key()));
            answers.add(Map.of("itemKey", item.key(), "answer", item.gradable().answer()));
        }
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctCount").value(0))
                .andExpect(jsonPath("$.data.allCorrect").value(false))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
        mockMvc.perform(api.authGet(path, token))
                .andExpect(jsonPath("$.data.done").value(false));
    }

    /**
     * A Listening skip whose audio did not play ("Audio didn't load") shows the answer and counts for nothing: no
     * mistake and no answer in the week's counts, so a hint opened for the item still counts for its next answer. The
     * flag excuses only that: an answer given anyway, a skip whose audio played and a skip of a set without audio are
     * mistakes as usual.
     */
    @Test
    void aListeningSkipWhoseAudioDidNotPlayIsNotCounted() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-audio"));
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        String path = "/api/v1/students/practice/listening/sets/" + set.getId();
        List<PracticeCatalog.Item> items = practiceCatalog.content(PracticeType.LISTENING, set).items();
        PracticeCatalog.Item first = items.get(0);
        JsonNode run = api.data(mockMvc.perform(api.authGet(path, token)).andExpect(status().isOk()).andReturn());

        mockMvc.perform(api.jsonPost(path + "/hint", token, Map.of("itemKey", first.key(),
                        "runStartedAt", run.path("runStartedAt").asText(),
                        "runSignature", run.path("runSignature").asText())))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(path + "/check", token, skip(first, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value("一杯茶"))
                .andExpect(jsonPath("$.data.explanation").isNotEmpty());
        mockMvc.perform(api.jsonPost(path + "/check", token,
                        Map.of("itemKey", first.key(), "answer", "\u00a0\u202f", "audioFailed", true, "confidence", "SURE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.all").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/progress/answers", token))
                .andExpect(jsonPath("$.data.hintsUsed").value(0));
        mockMvc.perform(api.jsonPost(path + "/check", token,
                        Map.of("itemKey", first.key(), "answer", "一杯茶", "confidence", "SURE")))
                .andExpect(jsonPath("$.data.correct").value(true));
        mockMvc.perform(api.authGet("/api/v1/students/progress/answers", token))
                .andExpect(jsonPath("$.data.sureAndRight").value(0))
                .andExpect(jsonPath("$.data.luckyGuesses").value(1))
                .andExpect(jsonPath("$.data.hintsUsed").value(1));

        PracticeCatalog.Item second = items.get(1);
        mockMvc.perform(api.jsonPost(path + "/check", token,
                        Map.of("itemKey", second.key(), "answer", wrongOption(second), "audioFailed", true)))
                .andExpect(jsonPath("$.data.correct").value(false));
        mockMvc.perform(api.jsonPost(path + "/check", token, skip(items.get(2), false)))
                .andExpect(jsonPath("$.data.correct").value(false));
        PracticeActivityContent sentences = practiceSet(PracticeType.DRAG_DROP, "Places on Campus");
        PracticeCatalog.Item sentence = practiceCatalog.content(PracticeType.DRAG_DROP, sentences).items().get(0);
        mockMvc.perform(api.jsonPost("/api/v1/students/practice/drag-drop/sets/" + sentences.getId() + "/check",
                        token, skip(sentence, true)))
                .andExpect(jsonPath("$.data.correct").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.all").value(3))
                .andExpect(jsonPath("$.data.practice").value(3));
    }

    /** A skip of an item, saying whether its audio did not play. */
    private static Map<String, Object> skip(PracticeCatalog.Item item, boolean audioFailed) {
        Map<String, Object> skip = new HashMap<>();
        skip.put("itemKey", item.key());
        skip.put("answer", null);
        skip.put("audioFailed", audioFailed);
        return skip;
    }

    @Test
    void sentencesAreBuiltFromTilesAndMatchingPairsAreGraded() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-build"));
        PracticeActivityContent sentences = practiceSet(PracticeType.DRAG_DROP, "Places on Campus");
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

        PracticeActivityContent pairs = practiceSet(PracticeType.MATCHING, "Numbers Match");
        String pairsPath = "/api/v1/students/practice/matching/sets/" + pairs.getId();
        JsonNode game = api.data(mockMvc.perform(api.authGet(pairsPath, token)).andReturn());
        assertThat(game.path("matchingChoices").size()).isEqualTo(4);
        List<Map<String, String>> matches = practiceCatalog.content(PracticeType.MATCHING, pairs).items().stream()
                .map(item -> Map.of("itemKey", item.key(), "answer", item.data().meaning()))
                .toList();
        mockMvc.perform(api.jsonPost(pairsPath + "/complete", token, Map.of("answers", matches)))
                .andExpect(jsonPath("$.data.allCorrect").value(true))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(15));
    }

    @ParameterizedTest
    @CsvSource({
            "FLASHCARDS, Campus Words, update flashcard_items set meaning = ' ' where flashcard_id = ? order by id limit 1",
            "MATCHING, Numbers Match, update matching_game_items set right_text = ' ' where matching_game_id = ? order by id limit 1",
            "MATCHING, Numbers Match, update matching_game_items set right_text = 'five' where matching_game_id = ? and left_text = '三'",
            "DRAG_DROP, Places on Campus, update drag_drop_items set drag_text = '。' where drag_drop_activity_id = ? order by id limit 1"
    })
    @Transactional
    void olderSetsNeedGradableMaterialToStartOrFinish(PracticeType type, String title, String sql) throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-missing-material"));
        var set = practiceSet(type, title);
        assertThat(jdbc.update(sql, set.getId())).isOne();
        String slug = type.name().toLowerCase(Locale.ROOT).replace('_', '-');
        assertCannotPlay(token, "/api/v1/students/practice/" + slug + "/sets/" + set.getId());
    }

    @Test
    @Transactional
    void anOlderListeningQuestionNeedsAudioAndAnAnswerTheLearnerCanSelect() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-listening-material"));
        var set = (ListeningExercise) practiceSet(PracticeType.LISTENING, "At the Café");
        String path = "/api/v1/students/practice/listening/sets/" + set.getId();
        String question = """
                {"questions":[{"audioText":"茶","options":[{"text":"tea"}],"answer":"tea"}]}
                """;
        set.setQuestionPayload(question);
        mockMvc.perform(api.authGet(path, token)).andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(path + "/check", token, Map.of("itemKey", "0", "answer", "tea")))
                .andExpect(jsonPath("$.data.correct").value(true));

        set.setQuestionPayload(question.replace("\"answer\":\"tea\"", "\"answer\":\"coffee\""));
        assertCannotPlay(token, path);
        set.setQuestionPayload(question.replace("茶", " "));
        assertCannotPlay(token, path);
    }

    private void assertCannotPlay(String token, String path) throws Exception {
        mockMvc.perform(api.authGet(path, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This activity has no learning material yet."));
        mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("answers", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This activity has no learning material yet."));
    }

    @Test
    void aPublishedTypingWordNeedsPinyinThatCanProduceCandidates() throws Exception {
        String student = api.registerStudent(uniqueEmail("practice-typing-legacy"));
        String title = "Unplayable spelling " + uniqueSuffix();
        jdbc.update("""
                insert into typing_exercises (title, word_payload, xp_reward, content_status, created_at, updated_at)
                values (?, '{"words":[{"hanzi":"猫","pinyin":"123","meaning":"cat"}]}', 2, 'PUBLISHED', now(), now())
                """, title);
        Long id = jdbc.queryForObject("select id from typing_exercises where title = ?", Long.class, title);
        try {
            mockMvc.perform(api.authGet("/api/v1/students/practice/typing/sets/" + id, student))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This activity has no learning material yet."));
        } finally {
            jdbc.update("delete from typing_exercises where id = ?", id);
        }
    }

    @Test
    void tracingNeedsEveryStroke() throws Exception {
        String token = api.registerStudent(uniqueEmail("practice-trace"));
        TracingExercise set = (TracingExercise) practiceSet(PracticeType.TRACING, "Write 学");
        String path = "/api/v1/students/practice/tracing/sets/" + set.getId();
        Integer strokes = set.getStrokeCount();
        set.setStrokeCount(null);
        tracingRepository.save(set);
        try {
            mockMvc.perform(api.authGet(path, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This activity has no learning material yet."));
            mockMvc.perform(api.jsonPost(path + "/complete", token, Map.of("strokeCount", 8)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This activity has no learning material yet."));
        } finally {
            set.setStrokeCount(strokes);
            tracingRepository.save(set);
        }
        mockMvc.perform(api.authGet(path, token))
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.strokeCount").value(8))
                .andExpect(jsonPath("$.data.characterStrokes[0]").value(8))
                .andExpect(jsonPath("$.data.items[0].hanzi").value("学"));
        // A word: the strokes of each character, "Character 1 of 2 · 0 of 4 strokes".
        mockMvc.perform(api.authGet("/api/v1/students/practice/tracing/sets/"
                        + practiceSet(PracticeType.TRACING, "Write 中文").getId(), token))
                .andExpect(jsonPath("$.data.strokeCount").value(8))
                .andExpect(jsonPath("$.data.characterStrokes.length()").value(2))
                .andExpect(jsonPath("$.data.characterStrokes[0]").value(4));
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
        ListeningExercise set = (ListeningExercise) practiceSet(PracticeType.LISTENING, "Weather Today");
        jdbc.update("update listening_exercises set content_status = ? where id = ?",
                ContentStatus.DRAFT.name(), set.getId());
        try {
            mockMvc.perform(api.authGet("/api/v1/students/practice/listening/sets/" + set.getId(), token))
                    .andExpect(status().isNotFound());
            JsonNode sets = api.data(mockMvc.perform(api.authGet("/api/v1/students/practice/listening/sets", token))
                    .andReturn());
            assertThat(sets.findValuesAsText("title")).doesNotContain("Weather Today");
        } finally {
            jdbc.update("update listening_exercises set content_status = ? where id = ?",
                    ContentStatus.PUBLISHED.name(), set.getId());
        }
        mockMvc.perform(api.authGet("/api/v1/students/practice/listening/sets", api.adminToken()))
                .andExpect(status().isForbidden());
    }
}
