package com.example.aichinese.dto.commerce;

public record PaymentProviderCheckoutResult(
        boolean available,
        String providerName,
        String sessionReference,
        String checkoutUrl,
        String providerStatus,
        String message) {

    public static final String UNAVAILABLE_PROVIDER_STATUS = "UNAVAILABLE";

    public static PaymentProviderCheckoutResult unavailable(String providerName, String message) {
        return new PaymentProviderCheckoutResult(
                false, providerName, null, null, UNAVAILABLE_PROVIDER_STATUS, message);
    }
}
