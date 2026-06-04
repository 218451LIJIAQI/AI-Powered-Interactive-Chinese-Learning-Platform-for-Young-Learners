package com.example.aichinese.provider;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.service.model.CommerceCheckout;

@Component
public class NoOpPaymentProviderClient implements PaymentProviderClient {

    private static final String PROVIDER_NAME = "no-op";

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        return PaymentProviderCheckoutResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
    }

    @Override
    public PaymentProviderStatusResult getPaymentStatus(String providerReference) {
        return PaymentProviderStatusResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
    }
}
