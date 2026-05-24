package com.example.aichinese.provider;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.service.model.CommerceCheckout;

/**
 * Default {@link PaymentProviderClient} that always reports the
 * payment service as unavailable.
 *
 * <p>Picked up automatically when {@code app.payment.provider=no-op}
 * (the safe default for fresh deployments). With this implementation
 * active, every checkout attempt cleanly short-circuits with the
 * canonical "payments not configured" message — no external calls,
 * no test-mode payments, no risk of accidentally accepting money on a
 * server that has not been operationally provisioned for it.</p>
 *
 * <p>Switching to {@code mock} or {@code stripe} requires an explicit
 * configuration change, so the no-op state is failure-by-default
 * rather than failure-after-misconfiguration.</p>
 */
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
