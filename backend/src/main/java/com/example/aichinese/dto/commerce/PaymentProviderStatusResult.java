package com.example.aichinese.dto.commerce;

import com.example.aichinese.entity.enums.PaymentStatus;

public record PaymentProviderStatusResult(
        boolean available,
        String providerName,
        String sessionReference,
        PaymentStatus paymentStatus,
        String providerStatus,
        String message) {

    public static final String UNAVAILABLE_PROVIDER_STATUS =
            PaymentProviderCheckoutResult.UNAVAILABLE_PROVIDER_STATUS;

    public static PaymentProviderStatusResult unavailable(String providerName, String message) {
        return new PaymentProviderStatusResult(
                false, providerName, null, PaymentStatus.FAILED, UNAVAILABLE_PROVIDER_STATUS, message);
    }
}
