package com.example.aichinese.dto.commerce;

import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "amount", "requestedNote" })
public record PayoutRequestCreateRequest(
        @NotNull(message = "Payout amount is required.")
        @DecimalMin(value = "0.01", inclusive = true,
                message = "Payout amount must be at least {value}.")
        @Digits(integer = CommerceDtoConstants.MONEY_INTEGER_DIGITS,
                fraction = CommerceDtoConstants.MONEY_FRACTION_DIGITS,
                message = "Payout amount must fit within {integer} integer digits and {fraction} decimal places.")
        BigDecimal amount,
        @Size(max = CommerceDtoConstants.PAYOUT_NOTE_MAX_LENGTH,
                message = "Payout note must be {max} characters or fewer.")
        String requestedNote) {

    public PayoutRequestCreateRequest {
        requestedNote = stripToNull(requestedNote);
    }
}
