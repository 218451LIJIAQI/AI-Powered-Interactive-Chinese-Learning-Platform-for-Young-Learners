package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnQuestionType;

/**
 * A question as the student sees it, without its answer: a Learn question, or a waiting mistake in the review.
 * Fields that would give the answer away are left out for the question type (no meaning on a meaning question, no
 * hanzi on a listening question). {@code type} is set when the interface writes the instruction, {@code prompt}
 * when the question carries its own wording (a Practice quiz question).
 */
public record QuestionView(
        Long id,
        AnswerKind answerKind,
        LearnQuestionType type,
        String prompt,
        String hanzi,
        String pinyin,
        String meaning,
        String audioText,
        List<ChoiceOption> options,
        List<String> tiles) {

    public QuestionView {
        options = options == null ? List.of() : List.copyOf(options);
        tiles = tiles == null ? List.of() : List.copyOf(tiles);
    }
}
