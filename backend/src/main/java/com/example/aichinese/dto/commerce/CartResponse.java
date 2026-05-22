package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        String currencyCode,
        boolean empty) {
}
