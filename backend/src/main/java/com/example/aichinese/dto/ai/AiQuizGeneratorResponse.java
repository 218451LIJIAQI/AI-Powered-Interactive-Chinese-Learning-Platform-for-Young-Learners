package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.util.List;

import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.QuizQuestionType;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiQuizGeneratorResponse(
        String title,
        List<Question> questions,
        AiRequestStatus status,
        String message) {

    @Override
    public String toString() {
        return "AiQuizGeneratorResponse[title=" + redactLength(title)
                + ", questions=" + (questions == null ? 0 : questions.size())
                + ", status=" + status
                + ", message=" + redactLength(message) + "]";
    }

    @Schema(name = "AiGeneratedQuizQuestion")
    public record Question(
            String questionText,
            QuizQuestionType questionType,
            List<String> options,
            String correctAnswer,
            String explanation,
            String pinyin,
            String translation) {

        @Override
        public String toString() {
            return "Question[questionType=" + questionType
                    + ", questionText=" + redactLength(questionText)
                    + ", options=" + (options == null ? 0 : options.size())
                    + ", correctAnswer=" + redactLength(correctAnswer)
                    + ", explanation=" + redactLength(explanation) + "]";
        }
    }
}
