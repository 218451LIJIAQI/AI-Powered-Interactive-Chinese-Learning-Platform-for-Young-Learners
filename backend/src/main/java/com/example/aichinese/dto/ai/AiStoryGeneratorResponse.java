package com.example.aichinese.dto.ai;

import java.util.List;

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
}
