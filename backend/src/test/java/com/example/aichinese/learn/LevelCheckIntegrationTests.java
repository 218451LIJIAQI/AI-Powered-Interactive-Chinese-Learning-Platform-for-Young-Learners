package com.example.aichinese.learn;

import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.support.ApiIntegrationTest;

/** The stateless adaptive level check the student setup calls. */
class LevelCheckIntegrationTests extends ApiIntegrationTest {

    private static final String NEXT = "/api/v1/students/level-check/next";

    @Autowired
    private LevelCheckQuestionRepository questionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Test
    void theFirstQuestionStartsAtTheChosenLevelWithoutItsAnswer() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-start"));
        JsonNode first = api.data(mockMvc.perform(api.jsonPost(NEXT, token, Map.of("answers", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").doesNotExist())
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.maxQuestions").value(8))
                .andExpect(jsonPath("$.data.next.skill").value("VOCABULARY"))
                .andReturn());
        assertThat(first.toString()).doesNotContain("correct").doesNotContain("explanation");
        assertThat(levelOf(first)).isEqualTo(LevelCheckLevel.BEGINNER_1);

        JsonNode harder = api.data(mockMvc.perform(api.jsonPost(NEXT, token,
                        Map.of("startLevel", "BEGINNER_2", "answers", List.of())))
                .andReturn());
        assertThat(levelOf(harder)).isEqualTo(LevelCheckLevel.BEGINNER_2);
    }

    @Test
    void theCheckAsksOneQuestionPerSkillInOrderUpToEight() throws Exception {
        // Alternating "don't know" and right answers never makes the level clear early: every skill gets its question.
        String token = api.registerStudent(uniqueEmail("check-skills"));
        List<Map<String, Object>> answers = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        JsonNode step = next(token, answers);
        while (!step.path("done").asBoolean()) {
            skills.add(step.path("next").path("skill").asText());
            long questionId = step.path("next").path("id").asLong();
            answers.add(levelCheckAnswer(questionId, skills.size() % 2 == 1 ? null : rightLevelCheckOption(questionId)));
            step = next(token, answers);
        }
        assertThat(skills).containsExactly(
                "VOCABULARY", "LISTENING", "CHARACTERS", "NUMBERS", "PINYIN", "GRAMMAR", "SENTENCES", "IDIOMS");
        assertThat(step.path("answeredCount").asInt()).isEqualTo(8);
    }

    @Test
    void rightAnswersGoHarderAndAnEarlyClearLevelPlacesTheStudent() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-right"));
        List<Map<String, Object>> answers = new ArrayList<>();
        List<LevelCheckLevel> askedLevels = new ArrayList<>();
        JsonNode step = next(token, answers);
        while (!step.path("done").asBoolean()) {
            long questionId = step.path("next").path("id").asLong();
            askedLevels.add(levelOf(step));
            answers.add(levelCheckAnswer(questionId, rightLevelCheckOption(questionId)));
            step = next(token, answers);
            assertThat(step.path("feedback").path("correct").asBoolean()).isTrue();
        }
        assertThat(answers.size()).isBetween(3, 7);
        assertThat(askedLevels).containsSubsequence(LevelCheckLevel.BEGINNER_1, LevelCheckLevel.BEGINNER_2);
        assertThat(step.path("result").path("level").asText()).isEqualTo("BEGINNER_2");
        assertThat(step.path("result").path("newSkills").size()).isZero();
        assertThat(step.path("next").isNull()).isTrue();
    }

    @Test
    void notKnowingStopsEarlyAsNewToMandarin() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-new"));
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode step = next(token, answers);
        for (int count = 0; count < 3; count++) {
            answers.add(levelCheckAnswer(step.path("next").path("id").asLong(), null));
            step = next(token, answers);
            assertThat(step.path("feedback").path("skipped").asBoolean()).isTrue();
            assertThat(step.path("feedback").path("correctAnswer").asText()).isNotBlank();
        }
        assertThat(step.path("done").asBoolean()).isTrue();
        assertThat(step.path("result").path("level").asText()).isEqualTo("NEW_TO_MANDARIN");
    }

    @Test
    void aWrongAnswerExplainsWhyAndCountsInTheStatistics() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-wrong"));
        JsonNode first = next(token, List.of());
        long questionId = first.path("next").path("id").asLong();
        LevelCheckQuestion before = questionRepository.findById(questionId).orElseThrow();
        LevelCheckOption wrong = levelCheckOptionRepository.findByQuestionIdOrderByPositionAsc(questionId).stream()
                .filter(option -> !option.isCorrect())
                .findFirst()
                .orElseThrow();
        JsonNode step = next(token, List.of(levelCheckAnswer(questionId, wrong.getId())));
        assertThat(step.path("feedback").path("correct").asBoolean()).isFalse();
        assertThat(step.path("feedback").path("explanation").asText()).isEqualTo(wrong.getExplanation());
        LevelCheckQuestion after = questionRepository.findById(questionId).orElseThrow();
        assertThat(after.getAnsweredCount()).isEqualTo(before.getAnsweredCount() + 1);
        assertThat(after.getCorrectCount()).isEqualTo(before.getCorrectCount());

        mockMvc.perform(api.jsonPost(NEXT, token, Map.of("answers", List.of(levelCheckAnswer(987_654L, null)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void oldDuplicateLabelsHaveOneVisibleChoiceAndGradeEitherOriginalIdWithinThatQuestion() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-duplicate"));
        long questionId = next(token, List.of()).path("next").path("id").asLong();
        List<LevelCheckOption> options = levelCheckOptionRepository.findByQuestionIdOrderByPositionAsc(questionId);
        options.forEach(option -> option.setCorrect(false));
        LevelCheckOption earlier = options.getFirst();
        earlier.setOptionText(" 你好　");
        LevelCheckOption right = options.get(1);
        right.setOptionText("\u00a0你好");
        right.setCorrect(true);

        JsonNode visible = next(token, List.of()).path("next").path("options");
        assertThat(visible.size()).isEqualTo(options.size() - 1);
        assertThat(visible.get(0).path("id").asLong()).isEqualTo(right.getId());
        assertThat(visible.get(0).path("text").asText()).isEqualTo("你好");
        for (LevelCheckOption option : List.of(earlier, right)) {
            JsonNode feedback = next(token, List.of(levelCheckAnswer(questionId, option.getId()))).path("feedback");
            assertThat(feedback.path("correct").asBoolean()).isTrue();
            assertThat(feedback.path("correctAnswer").asText()).isEqualTo("你好");
        }

        long otherId = questionRepository.findPublishedOrAnswered(Set.of()).stream()
                .map(LevelCheckQuestion::getId).filter(id -> id != questionId).findFirst().orElseThrow();
        LevelCheckOption foreign = levelCheckOptionRepository.findByQuestionIdOrderByPositionAsc(otherId).getFirst();
        foreign.setOptionText("你好");
        JsonNode feedback = next(token, List.of(levelCheckAnswer(questionId, foreign.getId()))).path("feedback");
        assertThat(feedback.path("correct").asBoolean()).isFalse();
        assertThat(feedback.path("skipped").asBoolean()).isFalse();
    }

    @Test
    void aRightAnswerCountsInBothStatistics() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-counted"));
        long questionId = next(token, List.of()).path("next").path("id").asLong();
        LevelCheckQuestion before = questionRepository.findById(questionId).orElseThrow();

        Map<String, Object> right = levelCheckAnswer(questionId, rightLevelCheckOption(questionId));
        LevelCheckQuestion sameSkill = questionRepository.findPublishedOrAnswered(Set.of()).stream()
                .filter(question -> question.getSkill() == before.getSkill() && !question.getId().equals(questionId))
                .findFirst().orElseThrow();
        for (LevelCheckQuestion repeated : List.of(before, sameSkill)) {
            mockMvc.perform(api.jsonPost(NEXT, token, Map.of("answers", List.of(right,
                            levelCheckAnswer(repeated.getId(), rightLevelCheckOption(repeated.getId()))))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Answer each level-check skill only once."));
            LevelCheckQuestion unchanged = questionRepository.findById(repeated.getId()).orElseThrow();
            assertThat(unchanged.getAnsweredCount()).isEqualTo(repeated.getAnsweredCount());
            assertThat(unchanged.getCorrectCount()).isEqualTo(repeated.getCorrectCount());
        }
        next(token, List.of(right));

        LevelCheckQuestion after = questionRepository.findById(questionId).orElseThrow();
        assertThat(after.getAnsweredCount()).isEqualTo(before.getAnsweredCount() + 1);
        assertThat(after.getCorrectCount()).isEqualTo(before.getCorrectCount() + 1);
    }

    @Test
    void editingAQuestionKeepsAnAnswerCountedAfterItWasLoaded() throws Exception {
        LevelCheckQuestion question = new LevelCheckQuestion();
        question.setPrompt("What does 你好 mean?");
        question.setMandarinLevel(LevelCheckLevel.BEGINNER_1);
        question.setDifficulty(ActivityDifficulty.EASY);
        question.setSkill(LevelCheckSkill.VOCABULARY);
        Long id = questionRepository.save(question).getId();
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            withPausedOperation(pause -> transaction.executeWithoutResult(status -> {
                LevelCheckQuestion editing = questionRepository.findById(id).orElseThrow();
                pause.run();
                editing.setPrompt("Choose the meaning of 你好.");
            }), () -> transaction.executeWithoutResult(status -> questionRepository.countAnswer(id, 1)));

            LevelCheckQuestion edited = questionRepository.findById(id).orElseThrow();
            assertThat(edited.getPrompt()).isEqualTo("Choose the meaning of 你好.");
            assertThat(edited.getAnsweredCount()).isEqualTo(1);
            assertThat(edited.getCorrectCount()).isEqualTo(1);
        } finally {
            questionRepository.deleteById(id);
        }
    }

    @Test
    void stillGradesAnAnswerIfItsQuestionWasUnpublishedDuringTheCheck() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-unpublished"));
        JsonNode first = next(token, List.of());
        long questionId = first.path("next").path("id").asLong();
        LevelCheckQuestion question = questionRepository.findById(questionId).orElseThrow();
        List<Long> skillQuestions = questionRepository.findPublishedOrAnswered(Set.of()).stream()
                .filter(published -> published.getSkill() == question.getSkill()).map(LevelCheckQuestion::getId).toList();
        String setStatus = "update level_check_questions set publish_status = :status where id in (:ids)";
        namedJdbc.update(setStatus, Map.of("status", PublishStatus.DRAFT.name(), "ids", skillQuestions));
        try {
            assertThat(next(token, List.of()).path("maxQuestions").asInt()).isEqualTo(first.path("maxQuestions").asInt() - 1);
            JsonNode step = next(token, List.of(levelCheckAnswer(questionId, rightLevelCheckOption(questionId))));
            assertThat(step.path("feedback").path("correct").asBoolean()).isTrue();
            assertThat(step.path("maxQuestions").asInt()).isEqualTo(first.path("maxQuestions").asInt());
            assertThat(step.path("next").path("id").asLong()).isPositive().isNotEqualTo(questionId);
        } finally {
            namedJdbc.update(setStatus, Map.of("status", PublishStatus.PUBLISHED.name(), "ids", skillQuestions));
        }
    }

    @Test
    void mixedBeginner2TrajectoryDoesNotStopEarlyBeforePlacementIsGuaranteed() throws Exception {
        // Starting at BEGINNER_2 (tier 3): miss B2 -> B1 right -> miss B2 -> B1 right -> 3 B2 right leaves 3/5 B2 (< 2/3).
        // Early-stop must wait for Q8 so answering Q8 right reaches 4/6 B2 and places at BEGINNER_2.
        String token = api.registerStudent(uniqueEmail("check-b2-mixed"));
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode step = next(token, "BEGINNER_2", answers);
        boolean[] rightPattern = { false, true, false, true, true, true, true, true };
        for (int index = 0; index < rightPattern.length; index++) {
            long questionId = step.path("next").path("id").asLong();
            Long optionId = rightPattern[index] ? rightLevelCheckOption(questionId) : wrongLevelCheckOption(questionId);
            answers.add(levelCheckAnswer(questionId, optionId));
            step = next(token, "BEGINNER_2", answers);
            if (index < rightPattern.length - 1) {
                assertThat(step.path("done").asBoolean()).as("question %d must not stop early", index + 1).isFalse();
            }
        }
        assertThat(step.path("done").asBoolean()).isTrue();
        assertThat(step.path("answeredCount").asInt()).isEqualTo(8);
        assertThat(step.path("result").path("level").asText()).isEqualTo("BEGINNER_2");
    }

    private JsonNode next(String token, List<Map<String, Object>> answers) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost(NEXT, token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode next(String token, String startLevel, List<Map<String, Object>> answers) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost(NEXT, token, Map.of("startLevel", startLevel, "answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private Long wrongLevelCheckOption(long questionId) {
        return levelCheckOptionRepository.findByQuestionIdOrderByPositionAsc(questionId).stream()
                .filter(option -> !option.isCorrect())
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private LevelCheckLevel levelOf(JsonNode step) {
        return questionRepository.findById(step.path("next").path("id").asLong()).orElseThrow().getMandarinLevel();
    }
}
