package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.strip;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Typing Practice IME lookup: the words the learner's typed pinyin spells. */
public final class TypingCandidatesDto {

    private TypingCandidatesDto() {
    }

    /** The words the typed pinyin spells, in the order a keyboard would offer them; empty when it spells none. */
    @Schema(name = "Candidates")
    public record Response(List<String> words) {
    }

    @Schema(name = "CandidatesRequest")
    public record Request(
            @NotBlank(message = "Item is required.")
            @Size(max = 40, message = "Item is required.")
            String itemKey,
            @NotBlank(message = "Type the pinyin first.")
            @Size(max = 200, message = "Pinyin must not exceed 200 characters.")
            String pinyin) {

        public Request {
            itemKey = strip(itemKey);
            pinyin = strip(pinyin);
        }
    }
}
