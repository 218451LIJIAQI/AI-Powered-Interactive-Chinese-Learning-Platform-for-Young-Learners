package com.example.aichinese.dto.learn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One option of a choice question: its text (hanzi or a meaning) and, for hanzi, its pinyin. */
public record ChoiceOption(
        @NotBlank(message = "Every answer needs text.")
        @Size(max = 200, message = "An answer must not exceed 200 characters.")
        String text,

        @Size(max = 200, message = "Pinyin must not exceed 200 characters.")
        String pinyin) {
}
