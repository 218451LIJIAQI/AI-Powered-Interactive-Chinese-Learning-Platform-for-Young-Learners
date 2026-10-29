package com.example.aichinese.dto.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;

/**
 * Level check questions for admins: the list row, the edit form's request and its detail (with answer statistics).
 */
public final class LevelCheckQuestionData {

    private LevelCheckQuestionData() {
    }

    /** {@code correctPercent} is null while nobody answered the question. */
    public record Row(
            Long id,
            String prompt,
            String hanzi,
            LevelCheckSkill skill,
            ActivityDifficulty difficulty,
            LevelCheckLevel level,
            PublishStatus status,
            int answeredCount,
            Integer correctPercent) {
    }

    public record Detail(
            Long id,
            String prompt,
            String hanzi,
            String pinyin,
            String audioText,
            LevelCheckSkill skill,
            ActivityDifficulty difficulty,
            LevelCheckLevel level,
            PublishStatus status,
            int answeredCount,
            Integer correctPercent,
            List<Option> options) {

        public Detail {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** One answer: its text, whether it is the right one, and why (or what it means instead). */
    public record Option(
            Long id,

            @NotBlank(message = "Every answer needs text.")
            @Size(max = 200, message = "An answer must not exceed 200 characters.")
            String text,

            boolean correct,

            @Size(max = 300, message = "Explanation must not exceed 300 characters.")
            String explanation) {
    }

    public record Request(
            @NotBlank(message = "Question is required.")
            @Size(max = 300, message = "Question must not exceed 300 characters.")
            String prompt,

            @Size(max = 100, message = "Chinese text must not exceed 100 characters.")
            String hanzi,

            @Size(max = 200, message = "Pinyin must not exceed 200 characters.")
            String pinyin,

            @Size(max = 200, message = "The Chinese read aloud must not exceed 200 characters.")
            String audioText,

            @NotNull(message = "Skill is required.")
            LevelCheckSkill skill,

            @NotNull(message = "Difficulty is required.")
            ActivityDifficulty difficulty,

            @NotNull(message = "Level is required.")
            LevelCheckLevel level,

            @NotNull(message = "Status is required.")
            PublishStatus status,

            @NotNull(message = "Add at least 2 answers.")
            @Size(min = 2, max = 6, message = "A question needs 2 to 6 answers.")
            List<@Valid Option> options) {
    }
}
