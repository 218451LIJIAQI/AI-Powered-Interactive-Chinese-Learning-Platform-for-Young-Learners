package com.example.aichinese.service;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.provider.MockPaymentProviderClient;
import com.example.aichinese.provider.NoOpPaymentProviderClient;
import com.example.aichinese.provider.PaymentProviderClient;
import com.example.aichinese.provider.StripePaymentProviderClient;

@Service
public class PaymentProviderService {

    private final PaymentProperties paymentProperties;
    private final StripePaymentProviderClient stripePaymentProviderClient;
    private final MockPaymentProviderClient mockPaymentProviderClient;
    private final NoOpPaymentProviderClient noOpPaymentProviderClient;

    public PaymentProviderService(
            PaymentProperties paymentProperties,
            StripePaymentProviderClient stripePaymentProviderClient,
            MockPaymentProviderClient mockPaymentProviderClient,
            NoOpPaymentProviderClient noOpPaymentProviderClient) {
        this.paymentProperties = paymentProperties;
        this.stripePaymentProviderClient = stripePaymentProviderClient;
        this.mockPaymentProviderClient = mockPaymentProviderClient;
        this.noOpPaymentProviderClient = noOpPaymentProviderClient;
    }

    public PaymentProviderClient getClient() {
        if (!"stripe".equalsIgnoreCase(paymentProperties.provider())) {
            return noOpPaymentProviderClient;
        }

        if (paymentProperties.isAvailableForRealCheckout()) {
            return stripePaymentProviderClient;
        }

        if (paymentProperties.mockSuccessEnabled()) {
            return mockPaymentProviderClient;
        }

        return noOpPaymentProviderClient;
    }
}
