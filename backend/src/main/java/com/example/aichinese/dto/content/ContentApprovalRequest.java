package com.example.aichinese.dto.content;

import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "note" })
public record ContentApprovalRequest(
        @Size(max = ContentDtoConstants.MODERATION_NOTE_MAX_LENGTH,
                message = "Approval note must not exceed 1000 characters.")
        String note) {

    public ContentApprovalRequest {
        note = stripToNull(note);
    }
}
