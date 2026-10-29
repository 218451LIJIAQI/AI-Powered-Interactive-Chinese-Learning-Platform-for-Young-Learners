package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;

/**
 * One step of the adaptive level check: feedback on the last answer (null on the first call), the next question
 * without its answer (null when done) and, when done, the placement result.
 */
public record LevelCheckNextResponse(
        Feedback feedback,
        Question next,
        boolean done,
        Result result,
        int answeredCount,
        int maxQuestions) {

    /** {@code correctAnswer}/{@code explanation}: the right option's text and why it is right (or why yours is not). */
    public record Feedback(boolean correct, boolean skipped, String correctAnswer, String explanation) {
    }

    public record Question(
            Long id,
            String prompt,
            String hanzi,
            String pinyin,
            String audioText,
            List<Option> options) {

        public Question {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Option(Long id, String text) {
    }

    /**
     * {@code headStart}: the student already knows some Mandarin (Beginner 1 or 2), so the first unit can be a quick
     * review. {@code knownSkills} were answered right every time; {@code newSkills} had a wrong or unknown answer.
     */
    public record Result(
            MandarinLevel level,
            boolean headStart,
            List<LevelCheckSkill> knownSkills,
            List<LevelCheckSkill> newSkills,
            int answeredCount) {

        public Result {
            knownSkills = knownSkills == null ? List.of() : List.copyOf(knownSkills);
            newSkills = newSkills == null ? List.of() : List.copyOf(newSkills);
        }
    }
}
