package com.example.aichinese.service.model;

import java.math.BigDecimal;
import java.util.Objects;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.PurchasableItemType;

public record ResolvedPurchasableItem(
        PurchasableItemType itemType,
        Long itemId,
        String title,
        BigDecimal unitPrice,
        String currencyCode,
        TeacherProfile teacher,
        boolean freeItem,
        String relatedFeatureKey) {

    public ResolvedPurchasableItem {
        itemType = Objects.requireNonNull(itemType, "itemType");
        itemId = Objects.requireNonNull(itemId, "itemId");
        title = requireText(title, "title");
        unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        currencyCode = requireText(currencyCode, "currencyCode");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value;
    }
}
