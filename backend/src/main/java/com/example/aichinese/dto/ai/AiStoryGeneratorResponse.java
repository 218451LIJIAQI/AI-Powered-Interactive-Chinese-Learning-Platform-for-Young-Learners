package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.util.List;

import com.example.aichinese.entity.enums.AiRequestStatus;

/** A generated beginner story for the student's reader: its lines, key words and comprehension questions. */
public record AiStoryGeneratorResponse(
        String title,
        String titleTranslation,
        List<AiStoryLineResponse> lines,
        List<VocabularyItem> vocabularyList,
        List<ComprehensionQuestion> comprehensionQuestions,
        AiRequestStatus status,
        String message) {

    @Override
    public String toString() {
        return "AiStoryGeneratorResponse[title=" + redactLength(title)
                + ", titleTranslation=" + redactLength(titleTranslation)
                + ", lines=" + (lines == null ? 0 : lines.size())
                + ", vocabularyList=" + (vocabularyList == null ? 0 : vocabularyList.size())
                + ", comprehensionQuestions=" + (comprehensionQuestions == null ? 0 : comprehensionQuestions.size())
                + ", status=" + status
                + ", message=" + redactLength(message) + "]";
    }

    public record VocabularyItem(
            String chineseWord,
            String pinyin,
            String englishMeaning,
            String bahasaMelayuMeaning) {

        @Override
        public String toString() {
            return "VocabularyItem[chineseWord=" + redactLength(chineseWord)
                    + ", pinyin=" + redactLength(pinyin)
                    + ", englishMeaning=" + redactLength(englishMeaning)
                    + ", bahasaMelayuMeaning=" + redactLength(bahasaMelayuMeaning) + "]";
        }
    }

    public record ComprehensionQuestion(String question, List<String> options, String answer) {

        @Override
        public String toString() {
            return "ComprehensionQuestion[question=" + redactLength(question)
                    + ", options=" + (options == null ? 0 : options.size())
                    + ", answer=" + redactLength(answer) + "]";
        }
    }
}
