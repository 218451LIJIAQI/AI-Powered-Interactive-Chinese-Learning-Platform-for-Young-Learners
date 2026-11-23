package com.example.aichinese.dto.learn;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;

/**
 * A Learn stop ready to play: its questions without answers. {@code passPercent} is set for a checkpoint (the share of
 * right answers it needs).
 *
 * @param runStartedAt the start of this lesson run, sent with each hint request so its allowance is counted separately
 * @param runSignature the server's signature of this run, sent back with {@code runStartedAt}
 */
public record LessonPlayResponse(
        String title,
        LearnLessonKind kind,
        Integer passPercent,
        List<QuestionView> questions,
        LocalDateTime runStartedAt,
        String runSignature) {
}
