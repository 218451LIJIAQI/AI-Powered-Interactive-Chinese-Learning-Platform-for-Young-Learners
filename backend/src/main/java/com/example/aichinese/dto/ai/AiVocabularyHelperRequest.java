package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "word",
        "inputLanguage",
        "outputLanguage",
        "learnerLevel",
        "languagePreference"
})
public record AiVocabularyHelperRequest(
        @NotBlank(message = "Word is required.")
        @Size(max = 200, message = "Word must not exceed 200 characters.")
        String word,
        @Size(max = 20, message = "Input language must not exceed 20 characters.")
        String inputLanguage,
        @Size(max = 20, message = "Output language must not exceed 20 characters.")
        String outputLanguage,
        @Size(max = 50, message = "Learner level must not exceed 50 characters.")
        String learnerLevel,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public AiVocabularyHelperRequest {
        word = strip(word);
        inputLanguage = stripToNull(inputLanguage);
        outputLanguage = stripToNull(outputLanguage);
        learnerLevel = stripToNull(learnerLevel);
        languagePreference = stripToNull(languagePreference);
    }
}
