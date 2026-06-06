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
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId must be positive.");
        }
        title = requireText(title, "title");
        unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative.");
        }
        currencyCode = requireText(currencyCode, "currencyCode");
        relatedFeatureKey = optionalText(relatedFeatureKey);
    }

    private static String requireText(String value, String fieldName) {
        String stripped = Objects.requireNonNull(value, fieldName).strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return stripped;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
