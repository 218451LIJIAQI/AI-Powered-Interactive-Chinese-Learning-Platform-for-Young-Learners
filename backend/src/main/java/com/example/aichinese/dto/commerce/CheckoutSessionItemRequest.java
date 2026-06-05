package com.example.aichinese.dto.commerce;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "itemType", "itemId", "featureKey" })
public record CheckoutSessionItemRequest(
        @NotBlank(message = "Item type is required.")
        @Size(max = CommerceDtoConstants.ITEM_TYPE_MAX_LENGTH,
                message = "Item type must be {max} characters or fewer.")
        String itemType,
        @NotNull(message = "Item id is required.")
        @Positive(message = "Item id must be positive.")
        Long itemId,
        @Size(max = CommerceDtoConstants.FEATURE_KEY_MAX_LENGTH,
                message = "Feature key must be {max} characters or fewer.")
        String featureKey) {

    public CheckoutSessionItemRequest {
        itemType = strip(itemType);
        featureKey = stripToNull(featureKey);
    }
}
