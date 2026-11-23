package com.example.aichinese.dto.learn;

import com.example.aichinese.entity.enums.PracticeType;

/**
 * Practice answers over the last seven days, grouped by confidence and correctness. Hint usage also includes
 * activities that do not ask learners to rate their confidence.
 */
public record AnswerInsightsResponse(
        long sureAndRight,
        long luckyGuesses,
        long sureButWrong,
        long wrongGuesses,
        long hintsUsed,
        PracticeType mostHintsIn) {
}
