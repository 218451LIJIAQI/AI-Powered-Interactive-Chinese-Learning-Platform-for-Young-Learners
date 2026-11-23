package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.XiaomeiScenario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The conversation so far (oldest first), for the end-of-chat summary. */
public record XiaomeiConversationRequest(
        @NotNull(message = "Scenario is required.")
        XiaomeiScenario scenario,

        @NotEmpty(message = "The conversation has no turns yet.")
        @Size(max = 24, message = "A conversation sends at most 24 earlier turns.")
        List<@NotNull(message = "Conversation turn is required.") @Valid XiaomeiTurn> turns,

        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public XiaomeiConversationRequest {
        turns = immutableListOrEmpty(turns);
        languagePreference = stripToNull(languagePreference);
    }

    @Override
    public String toString() {
        return "XiaomeiConversationRequest[scenario=" + scenario
                + ", turns=" + turns.size()
                + ", languagePreference=" + languagePreference + "]";
    }
}
