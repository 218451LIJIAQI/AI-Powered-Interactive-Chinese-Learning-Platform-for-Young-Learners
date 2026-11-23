package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Level check questions for admins: the list row, the edit form's request and its detail (with answer statistics).
 */
public final class LevelCheckQuestionData {

    private LevelCheckQuestionData() {
    }

    @Schema(name = "LevelCheckQuestionRow")
    public record Row(
            Long id,
            String prompt,
            LevelCheckSkill skill,
            ActivityDifficulty difficulty,
            LevelCheckLevel level,
            PublishStatus status) {
    }

    /** {@code correctPercent} is null while nobody answered the question. */
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
    }

    /** One answer: its text, whether it is the right one, and why (or what it means instead). */
    public record Option(
            @NotBlank(message = "Every answer needs text.")
            @Size(max = 200, message = "An answer must not exceed 200 characters.")
            String text,

            boolean correct,

            @Size(max = 300, message = "Explanation must not exceed 300 characters.")
            String explanation) {

        public Option {
            text = strip(text);
            explanation = stripToNull(explanation);
        }
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
            List<@NotNull(message = "Every answer needs text.") @Valid Option> options) {

        public Request {
            prompt = strip(prompt);
            hanzi = stripToNull(hanzi);
            pinyin = stripToNull(pinyin);
            audioText = stripToNull(audioText);
            options = immutableListOrNull(options);
        }
    }
}
