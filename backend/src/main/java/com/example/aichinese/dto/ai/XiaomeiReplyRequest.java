package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.XiaomeiScenario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The student's next line to Xiaomei, with the conversation so far (oldest first). */
public record XiaomeiReplyRequest(
        @NotNull(message = "Scenario is required.")
        XiaomeiScenario scenario,

        @Valid
        @Size(max = 24, message = "A conversation sends at most 24 earlier turns.")
        List<XiaomeiTurn> turns,

        @NotBlank(message = "Message is required.")
        @Size(max = 200, message = "Message must not exceed 200 characters.")
        String message,

        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public XiaomeiReplyRequest {
        turns = turns == null ? List.of() : List.copyOf(turns);
        message = strip(message);
        languagePreference = stripToNull(languagePreference);
    }
}
