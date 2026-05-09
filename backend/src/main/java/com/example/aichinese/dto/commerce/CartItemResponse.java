package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

public record CartItemResponse(
        Long cartItemId,
        String itemType,
        Long itemId,
        String title,
        BigDecimal unitPrice,
        String currencyCode,
        Integer quantity,
        BigDecimal subtotal,
        boolean teacherOwned) {
}
