package com.example.aichinese.dto.ai;

import java.util.List;

/** A generated beginner story for the student's reader: its lines, key words and comprehension questions. */
public record AiStoryGeneratorResponse(
        String title,
        String titleTranslation,
        List<AiStoryLineResponse> lines,
        List<AiStoryVocabularyItemResponse> vocabularyList,
        List<AiStoryComprehensionQuestionResponse> comprehensionQuestions,
        String status) {

    public AiStoryGeneratorResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
        vocabularyList = vocabularyList == null ? List.of() : List.copyOf(vocabularyList);
        comprehensionQuestions = comprehensionQuestions == null ? List.of() : List.copyOf(comprehensionQuestions);
    }
}
