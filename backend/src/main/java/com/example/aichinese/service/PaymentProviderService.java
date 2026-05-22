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
    private final MockPaymentProviderClient mockPaymentProviderClient;
    private final NoOpPaymentProviderClient noOpPaymentProviderClient;
    private final StripePaymentProviderClient stripePaymentProviderClient;

    public PaymentProviderService(
            PaymentProperties paymentProperties,
            MockPaymentProviderClient mockPaymentProviderClient,
            NoOpPaymentProviderClient noOpPaymentProviderClient,
            StripePaymentProviderClient stripePaymentProviderClient) {
        this.paymentProperties = paymentProperties;
        this.mockPaymentProviderClient = mockPaymentProviderClient;
        this.noOpPaymentProviderClient = noOpPaymentProviderClient;
        this.stripePaymentProviderClient = stripePaymentProviderClient;
    }

    public PaymentProviderClient getClient() {
        if ("stripe".equals(paymentProperties.provider())) {
            return stripePaymentProviderClient;
        }

        if ("mock".equals(paymentProperties.provider()) && paymentProperties.mockSuccessEnabled()) {
            return mockPaymentProviderClient;
        }

        return noOpPaymentProviderClient;
    }
}
