package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "title",
        "chineseStory",
        "pinyin",
        "englishTranslation",
        "bahasaMelayuTranslation",
        "vocabularyList",
        "comprehensionQuestions",
        "encouragement",
        "usageMode",
        "status"
})
public record AiStoryGeneratorResponse(
        String title,
        String chineseStory,
        String pinyin,
        String englishTranslation,
        String bahasaMelayuTranslation,
        List<AiStoryVocabularyItemResponse> vocabularyList,
        List<AiStoryComprehensionQuestionResponse> comprehensionQuestions,
        String encouragement,
        String usageMode,
        String status) {

    public AiStoryGeneratorResponse {
        vocabularyList = vocabularyList == null ? List.of() : List.copyOf(vocabularyList);
        comprehensionQuestions = comprehensionQuestions == null
                ? List.of()
                : List.copyOf(comprehensionQuestions);
    }
}
