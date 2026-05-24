package com.example.aichinese.provider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.service.model.CommerceCheckout;

/**
 * Deterministic stand-in for the payment gateway used in development
 * and integration tests.
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>{@link #createCheckoutSession(CommerceCheckout)} synthesises a
 *       {@code mock-checkout-<uuid>} reference and a redirect URL that
 *       points back to the configured success page with
 *       {@code ?paymentRef=<reference>&status=success} appended.</li>
 *   <li>{@link #getPaymentStatus(String)} immediately returns
 *       {@link com.example.aichinese.entity.enums.PaymentStatus#PAID PAID},
 *       so the rest of the order pipeline (entitlement grant, audit
 *       log, points award) can run end-to-end without a real provider.</li>
 * </ul>
 *
 * <p>Both methods short-circuit to "unavailable" unless
 * {@code app.payment.mock-success-enabled=true} — even in dev, a
 * developer must opt in so the mock provider does not silently mark
 * orders as paid in environments that should use the real provider.
 * {@link com.example.aichinese.config.PaymentProductionGuard} forbids
 * the {@code mockSuccessEnabled} flag from being on in the
 * {@code prod} profile, so this implementation can never sign off real
 * money.</p>
 *
 * <h2>URL-encoding correctness</h2>
 * <p>{@link #buildCheckoutUrl(String, String)} preserves any existing
 * query string and fragment on the configured success URL, percent-
 * encodes the synthesized reference, and chooses the right
 * {@code ?} / {@code &} separator. This avoids "double {@code ?}"
 * URLs when an operator configures
 * {@code app.payment.success-url=https://app.example.com/return?utm=foo}.</p>
 */
@Component
public class MockPaymentProviderClient implements PaymentProviderClient {

    private final PaymentProperties paymentProperties;

    public MockPaymentProviderClient(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        if (!paymentProperties.mockSuccessEnabled()) {
            return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }

        String reference = "mock-checkout-" + UUID.randomUUID();
        String checkoutUrl = buildCheckoutUrl(checkout.successUrl(), reference);
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

    private String buildCheckoutUrl(String successUrl, String reference) {
        String baseUrl = successUrl == null || successUrl.isBlank() ? "/" : successUrl.trim();
        int fragmentIndex = baseUrl.indexOf('#');
        String fragment = fragmentIndex >= 0 ? baseUrl.substring(fragmentIndex) : "";
        String urlWithoutFragment = fragmentIndex >= 0 ? baseUrl.substring(0, fragmentIndex) : baseUrl;
        String separator = urlWithoutFragment.contains("?")
                ? (urlWithoutFragment.endsWith("?") || urlWithoutFragment.endsWith("&") ? "" : "&")
                : "?";
        return urlWithoutFragment
                + separator
                + "paymentRef=" + URLEncoder.encode(reference, StandardCharsets.UTF_8)
                + "&status=success"
                + fragment;
    }
}
