package com.example.aichinese.provider;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.service.model.CommerceCheckout;

@Component
public class NoOpPaymentProviderClient implements PaymentProviderClient {

    @Override
    public String providerName() {
        return "no-op";
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
    }

    @Override
    public PaymentProviderStatusResult getPaymentStatus(String providerReference) {
        return PaymentProviderStatusResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
    }
}
