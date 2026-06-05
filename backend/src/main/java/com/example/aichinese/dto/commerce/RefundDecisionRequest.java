package com.example.aichinese.dto.commerce;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "reason" })
public record RefundDecisionRequest(
        @NotBlank(message = "Decision reason is required.")
        @Size(max = CommerceDtoConstants.DECISION_REASON_MAX_LENGTH,
                message = "Decision reason must be {max} characters or fewer.")
        String reason) {

    public RefundDecisionRequest {
        reason = strip(reason);
    }
}
