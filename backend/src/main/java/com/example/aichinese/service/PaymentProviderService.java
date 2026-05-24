package com.example.aichinese.service;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.provider.MockPaymentProviderClient;
import com.example.aichinese.provider.NoOpPaymentProviderClient;
import com.example.aichinese.provider.PaymentProviderClient;
import com.example.aichinese.provider.StripePaymentProviderClient;

/**
 * Selects the active {@link PaymentProviderClient} at runtime based
 * on {@link PaymentProperties#provider()}.
 *
 * <p>Resolution order: {@code stripe} → Stripe live/test, {@code mock}
 * with {@code mockSuccessEnabled=true} → in-memory mock, otherwise
 * the {@link NoOpPaymentProviderClient} fail-safe (always returns
 * "unavailable"). Provider selection is per-call rather than
 * cached so a configuration flip propagates without restart.</p>
 */
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
