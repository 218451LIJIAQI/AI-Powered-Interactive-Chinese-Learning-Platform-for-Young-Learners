package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;

/**
 * A Learn stop ready to play: where it sits on the path and its questions without answers. {@code passPercent} is
 * set for a checkpoint (the share of right answers it needs).
 */
public record LessonPlayResponse(
        Long id,
        String title,
        LearnLessonKind kind,
        Long unitId,
        int unitNumber,
        String unitTitle,
        int position,
        int unitLessonCount,
        int minutes,
        int xpReward,
        Integer passPercent,
        List<QuestionView> questions) {

    public LessonPlayResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
