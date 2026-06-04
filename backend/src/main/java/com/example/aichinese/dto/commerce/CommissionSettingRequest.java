package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder({ "commissionRate" })
public record CommissionSettingRequest(
        @NotNull(message = "Commission rate is required.")
        @DecimalMin(value = CommerceDtoConstants.COMMISSION_RATE_MIN, inclusive = true,
                message = "Commission rate must be at least {value}.")
        @DecimalMax(value = CommerceDtoConstants.COMMISSION_RATE_MAX, inclusive = true,
                message = "Commission rate must not exceed {value}.")
        @Digits(integer = CommerceDtoConstants.COMMISSION_RATE_INTEGER_DIGITS,
                fraction = CommerceDtoConstants.COMMISSION_RATE_FRACTION_DIGITS,
                message = "Commission rate must fit within {integer} integer digit(s) and {fraction} decimal places.")
        BigDecimal commissionRate) {
}
