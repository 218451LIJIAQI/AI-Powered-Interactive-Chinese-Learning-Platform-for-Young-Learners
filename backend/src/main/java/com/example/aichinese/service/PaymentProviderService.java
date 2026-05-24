package com.example.aichinese.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderService.class);

    private final PaymentProperties paymentProperties;
    private final MockPaymentProviderClient mockPaymentProviderClient;
    private final NoOpPaymentProviderClient noOpPaymentProviderClient;
    private final StripePaymentProviderClient stripePaymentProviderClient;

    public PaymentProviderService(
            PaymentProperties paymentProperties,
            MockPaymentProviderClient mockPaymentProviderClient,
            NoOpPaymentProviderClient noOpPaymentProviderClient,
            StripePaymentProviderClient stripePaymentProviderClient) {
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
        this.mockPaymentProviderClient = Objects.requireNonNull(mockPaymentProviderClient, "mockPaymentProviderClient");
        this.noOpPaymentProviderClient = Objects.requireNonNull(noOpPaymentProviderClient, "noOpPaymentProviderClient");
        this.stripePaymentProviderClient = Objects.requireNonNull(stripePaymentProviderClient, "stripePaymentProviderClient");
    }

    public PaymentProviderClient getClient() {
        String provider = paymentProperties.provider();
        PaymentProviderClient client = switch (provider) {
            case "stripe" -> stripePaymentProviderClient;
            case "mock" -> paymentProperties.mockSuccessEnabled()
                    ? mockPaymentProviderClient
                    : noOpPaymentProviderClient;
            default -> noOpPaymentProviderClient;
        };
        log.debug("Resolved payment provider: {} -> {}", provider, client.getClass().getSimpleName());
        return client;
    }
}
