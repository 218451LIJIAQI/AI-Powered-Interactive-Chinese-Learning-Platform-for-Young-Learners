package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One earlier line of a Talk with Xiaomei conversation. The conversation lives in the student's browser and is sent
 * with each request; the server keeps none of it.
 */
public record XiaomeiTurn(
        @NotNull(message = "Turn speaker is required.")
        Speaker speaker,

        @NotBlank(message = "Turn text is required.")
        @Size(max = 300, message = "Turn text must not exceed 300 characters.")
        String text) {

    public XiaomeiTurn {
        text = strip(text);
    }

    public enum Speaker {
        XIAOMEI,
        STUDENT
    }
}
