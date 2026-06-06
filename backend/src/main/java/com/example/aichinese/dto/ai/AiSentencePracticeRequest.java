package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "sentence", "learnerLevel", "languagePreference" })
public record AiSentencePracticeRequest(
        @NotBlank(message = "Sentence is required.")
        @Size(max = 1000, message = "Sentence must not exceed 1000 characters.")
        String sentence,
        @Size(max = 50, message = "Learner level must not exceed 50 characters.")
        String learnerLevel,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public AiSentencePracticeRequest {
        sentence = strip(sentence);
        learnerLevel = stripToNull(learnerLevel);
        languagePreference = stripToNull(languagePreference);
    }
}
