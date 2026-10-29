package com.example.aichinese.dto.learn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One line of a Practice story: the Chinese, its pinyin and its translation. */
public record StoryLine(
        @NotBlank(message = "Every story line needs Chinese text.")
        @Size(max = 300, message = "A story line must not exceed 300 characters.")
        String hanzi,

        @Size(max = 500, message = "Story pinyin must not exceed 500 characters.")
        String pinyin,

        @Size(max = 500, message = "Story translation must not exceed 500 characters.")
        String translation) {
}
