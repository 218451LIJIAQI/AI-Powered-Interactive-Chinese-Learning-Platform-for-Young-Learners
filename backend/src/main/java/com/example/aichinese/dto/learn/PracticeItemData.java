package com.example.aichinese.dto.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One item of a Practice set as admins edit it. Which fields an item uses depends on the set's type:
 * <ul>
 *   <li>Flashcards: hanzi, pinyin, meaning, optional example and its meaning;</li>
 *   <li>Quizzes: prompt, optional hanzi and pinyin, options, the right option, explanation;</li>
 *   <li>Listening: prompt, the Chinese read aloud ({@code audioText}), options, the right option, explanation;</li>
 *   <li>Tracing: the characters to write (hanzi), pinyin, meaning;</li>
 *   <li>Drag &amp; Drop: the sentence as tiles separated by spaces (hanzi), pinyin, meaning, extra tiles;</li>
 *   <li>Matching: hanzi, pinyin, meaning (the pair);</li>
 *   <li>Story: a question about the story (prompt), options, the right option, explanation.</li>
 * </ul>
 */
public record PracticeItemData(
        Long id,

        @Size(max = 100, message = "Chinese text must not exceed 100 characters.")
        String hanzi,

        @Size(max = 200, message = "Pinyin must not exceed 200 characters.")
        String pinyin,

        @Size(max = 200, message = "Meaning must not exceed 200 characters.")
        String meaning,

        @Size(max = 300, message = "Question must not exceed 300 characters.")
        String prompt,

        @Size(max = 200, message = "The Chinese read aloud must not exceed 200 characters.")
        String audioText,

        @Size(max = 200, message = "Example must not exceed 200 characters.")
        String example,

        @Size(max = 300, message = "Example meaning must not exceed 300 characters.")
        String exampleMeaning,

        @Size(max = 6, message = "A question has at most 6 answers.")
        List<@Valid ChoiceOption> options,

        @PositiveOrZero(message = "Tick the right answer.")
        @Max(value = 5, message = "Tick the right answer.")
        Integer correctIndex,

        @Size(max = 500, message = "Explanation must not exceed 500 characters.")
        String explanation,

        @Size(max = 255, message = "Extra tiles must not exceed 255 characters.")
        String distractors) {

    public PracticeItemData {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
