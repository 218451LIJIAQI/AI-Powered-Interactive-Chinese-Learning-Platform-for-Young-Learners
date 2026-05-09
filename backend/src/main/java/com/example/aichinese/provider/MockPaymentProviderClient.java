package com.example.aichinese.provider;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.service.model.CommerceCheckout;

@Component
public class MockPaymentProviderClient implements PaymentProviderClient {

    private final PaymentProperties paymentProperties;

    public MockPaymentProviderClient(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    @Override
    public String providerName() {
        return "stripe";
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        if (!paymentProperties.mockSuccessEnabled()) {
            return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }

        String reference = "mock-checkout-" + UUID.randomUUID();
        String checkoutUrl = checkout.successUrl() + "?paymentRef=" + reference + "&status=success";
        return new PaymentProviderCheckoutResult(
                true,
                providerName(),
                reference,
                checkoutUrl,
                "MOCK_CREATED",
                "Mock checkout session created successfully.");
    }

    @Override
    public PaymentProviderStatusResult getPaymentStatus(String providerReference) {
        if (!paymentProperties.mockSuccessEnabled()) {
            return PaymentProviderStatusResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }

        return new PaymentProviderStatusResult(
                true,
                providerName(),
                providerReference,
                PaymentStatus.PAID,
                "MOCK_PAID",
                "Mock payment verified successfully.");
    }
}
