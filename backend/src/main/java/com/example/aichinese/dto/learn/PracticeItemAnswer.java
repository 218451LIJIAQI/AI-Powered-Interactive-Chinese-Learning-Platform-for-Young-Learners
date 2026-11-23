package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.AnswerConfidence;

/**
 * One answer to a Practice item (chosen option text, matched meaning, or tiles separated by spaces). A check may say
 * how sure the student was; an answer after a hint counts as a guess. Which hint steps came first is what the server
 * gave for the item ({@code served_hints}), never what a request says. A Listening skip may say that the item's audio
 * did not play in the student's browser ({@code audioFailed}, "Audio didn't load"): that skip is not counted.
 */
public record PracticeItemAnswer(
        @NotBlank(message = "Item is required.")
        @Size(max = 40, message = "Item is required.")
        String itemKey,

        @Size(max = 300, message = "Answer must not exceed 300 characters.")
        String answer,

        AnswerConfidence confidence,

        boolean audioFailed) {

    public PracticeItemAnswer {
        itemKey = strip(itemKey);
    }
}
