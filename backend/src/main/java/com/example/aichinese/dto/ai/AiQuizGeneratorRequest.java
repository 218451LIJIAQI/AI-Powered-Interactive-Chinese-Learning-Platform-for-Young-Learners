package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "topic",
        "level",
        "category",
        "numberOfQuestions",
        "questionTypes",
        "languagePreference"
})
public record AiQuizGeneratorRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 100, message = "Level must not exceed 100 characters.")
        String level,
        @Size(max = 100, message = "Category must not exceed 100 characters.")
        String category,
        @Min(value = 1, message = "Number of questions must be at least 1.")
        @Max(value = 10, message = "Number of questions must not exceed 10.")
        Integer numberOfQuestions,
        @Size(max = 10, message = "Question types must not exceed 10 items.")
        List<@Size(max = 40, message = "Question type must not exceed 40 characters.") String> questionTypes,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public AiQuizGeneratorRequest {
        topic = strip(topic);
        level = stripToNull(level);
        category = stripToNull(category);
        questionTypes = strippedStringListOrNull(questionTypes);
        languagePreference = stripToNull(languagePreference);
    }
}
