package com.example.aichinese.dto.commerce;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "orderId", "reason" })
public record RefundRequestCreateRequest(
        @NotNull(message = "Order id is required.")
        @Positive(message = "Order id must be positive.")
        Long orderId,
        @NotBlank(message = "Refund reason is required.")
        @Size(max = CommerceDtoConstants.REFUND_REASON_MAX_LENGTH,
                message = "Refund reason must be {max} characters or fewer.")
        String reason) {

    public RefundRequestCreateRequest {
        reason = strip(reason);
    }
}
