package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.XiaomeiScenario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The conversation so far (oldest first), for "Help me reply" ideas or the end-of-chat summary. */
public record XiaomeiConversationRequest(
        @NotNull(message = "Scenario is required.")
        XiaomeiScenario scenario,

        @Valid
        @NotEmpty(message = "The conversation has no turns yet.")
        @Size(max = 24, message = "A conversation sends at most 24 earlier turns.")
        List<XiaomeiTurn> turns,

        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public XiaomeiConversationRequest {
        turns = turns == null ? List.of() : List.copyOf(turns);
        languagePreference = stripToNull(languagePreference);
    }
}
