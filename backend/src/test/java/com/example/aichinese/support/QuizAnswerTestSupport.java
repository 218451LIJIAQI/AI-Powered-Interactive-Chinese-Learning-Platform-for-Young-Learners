package com.example.aichinese.support;

import java.util.List;
import java.util.Map;

import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.repository.QuizOptionRepository;

/**
 * Since W4 option-based quiz questions are answered (and scored) by option id; this helper keeps
 * older tests readable by choosing the option with the given text. A text that is not one of the
 * options picks an incorrect option, so "wrong answer" scenarios still submit a valid attempt.
 */
public final class QuizAnswerTestSupport {

    private QuizAnswerTestSupport() {
    }

    public static Map<String, Object> optionAnswer(QuizOptionRepository repository, Long questionId, String optionText) {
        List<QuizOption> options = repository.findByQuestionIdOrderBySortOrderAscCreatedAtAsc(questionId);
        if (options.isEmpty()) {
            return Map.of("questionId", questionId, "answerText", optionText);
        }
        QuizOption chosen = options.stream()
                .filter(option -> option.getOptionText().equalsIgnoreCase(optionText.strip()))
                .findFirst()
                .orElseGet(() -> options.stream()
                        .filter(option -> !option.isCorrectOption())
                        .findFirst()
                        .orElse(options.get(0)));
        return Map.of("questionId", questionId, "optionId", chosen.getId());
    }
}
