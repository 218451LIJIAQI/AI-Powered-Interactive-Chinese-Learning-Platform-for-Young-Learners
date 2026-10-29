package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.repository.LevelCheckOptionRepository;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.support.ApiIntegrationTest;

/** The stateless adaptive level check the student setup calls. */
class LevelCheckIntegrationTests extends ApiIntegrationTest {

    private static final String NEXT = "/api/v1/students/level-check/next";

    @Autowired
    private LevelCheckOptionRepository optionRepository;

    @Autowired
    private LevelCheckQuestionRepository questionRepository;

    @Test
    void theFirstQuestionStartsAtTheChosenLevelWithoutItsAnswer() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-start"));
        JsonNode first = api.data(mockMvc.perform(api.jsonPost(NEXT, token, Map.of("answers", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").doesNotExist())
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.maxQuestions").value(6))
                .andReturn());
        assertThat(first.toString()).doesNotContain("correct").doesNotContain("explanation");
        assertThat(levelOf(first)).isEqualTo(LevelCheckLevel.BEGINNER_1);

        JsonNode harder = api.data(mockMvc.perform(api.jsonPost(NEXT, token,
                        Map.of("startLevel", "BEGINNER_2", "answers", List.of())))
                .andReturn());
        assertThat(levelOf(harder)).isEqualTo(LevelCheckLevel.BEGINNER_2);
    }

    @Test
    void rightAnswersGoHarderAndSixAnswersPlaceTheStudent() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-right"));
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode step = next(token, answers);
        while (!step.path("done").asBoolean()) {
            long questionId = step.path("next").path("id").asLong();
            answers.add(answer(questionId, rightOption(questionId)));
            step = next(token, answers);
            assertThat(step.path("feedback").path("correct").asBoolean()).isTrue();
        }
        assertThat(answers.size()).isBetween(3, 6);
        assertThat(step.path("result").path("level").asText()).isEqualTo("BEGINNER_2");
        assertThat(step.path("result").path("headStart").asBoolean()).isTrue();
        assertThat(step.path("result").path("newSkills").size()).isZero();
        assertThat(step.path("next").isNull()).isTrue();
    }

    @Test
    void notKnowingStopsEarlyAsNewToMandarin() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-new"));
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode step = next(token, answers);
        for (int count = 0; count < 3; count++) {
            answers.add(answer(step.path("next").path("id").asLong(), null));
            step = next(token, answers);
            assertThat(step.path("feedback").path("skipped").asBoolean()).isTrue();
            assertThat(step.path("feedback").path("correctAnswer").asText()).isNotBlank();
        }
        assertThat(step.path("done").asBoolean()).isTrue();
        assertThat(step.path("result").path("level").asText()).isEqualTo("NEW_TO_MANDARIN");
        assertThat(step.path("result").path("headStart").asBoolean()).isFalse();
    }

    @Test
    void aWrongAnswerExplainsWhyAndCountsInTheStatistics() throws Exception {
        String token = api.registerStudent(uniqueEmail("check-wrong"));
        JsonNode first = next(token, List.of());
        long questionId = first.path("next").path("id").asLong();
        int answeredBefore = questionRepository.findById(questionId).orElseThrow().getAnsweredCount();
        LevelCheckOption wrong = optionRepository.findByQuestionIdOrderByPositionAsc(questionId).stream()
                .filter(option -> !option.isCorrect())
                .findFirst()
                .orElseThrow();
        JsonNode step = next(token, List.of(answer(questionId, wrong.getId())));
        assertThat(step.path("feedback").path("correct").asBoolean()).isFalse();
        assertThat(step.path("feedback").path("explanation").asText()).isEqualTo(wrong.getExplanation());
        assertThat(questionRepository.findById(questionId).orElseThrow().getAnsweredCount()).isEqualTo(answeredBefore + 1);

        mockMvc.perform(api.jsonPost(NEXT, token, Map.of("answers", List.of(answer(987_654L, null)))))
                .andExpect(status().isBadRequest());
    }

    private JsonNode next(String token, List<Map<String, Object>> answers) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost(NEXT, token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private LevelCheckLevel levelOf(JsonNode step) {
        return questionRepository.findById(step.path("next").path("id").asLong()).orElseThrow().getMandarinLevel();
    }

    private Long rightOption(long questionId) {
        return optionRepository.findByQuestionIdOrderByPositionAsc(questionId).stream()
                .filter(LevelCheckOption::isCorrect)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static Map<String, Object> answer(long questionId, Long optionId) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("questionId", questionId);
        answer.put("optionId", optionId);
        return answer;
    }
}
