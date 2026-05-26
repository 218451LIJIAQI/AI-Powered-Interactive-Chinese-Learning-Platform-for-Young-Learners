package com.example.aichinese.provider;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

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

    private static final String PROVIDER_NAME = "mock";
    private static final String REFERENCE_PREFIX = "mock-checkout-";
    private static final String RESULT_CODE_CREATED = "MOCK_CREATED";
    private static final String RESULT_CODE_PAID = "MOCK_PAID";
    private static final String CREATED_MESSAGE = "Mock checkout session created successfully.";
    private static final String PAID_MESSAGE = "Mock payment verified successfully.";

    private final PaymentProperties paymentProperties;

    public MockPaymentProviderClient(PaymentProperties paymentProperties) {
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        if (!paymentProperties.mockSuccessEnabled() || checkout == null) {
            return PaymentProviderCheckoutResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
        }

        String reference = REFERENCE_PREFIX + UUID.randomUUID();
        String checkoutUrl = buildCheckoutUrl(checkout.successUrl(), reference);
        return new PaymentProviderCheckoutResult(
                true,
                PROVIDER_NAME,
                reference,
                checkoutUrl,
                RESULT_CODE_CREATED,
                CREATED_MESSAGE);
    }

    @Override
    public PaymentProviderStatusResult getPaymentStatus(String providerReference) {
        if (!paymentProperties.mockSuccessEnabled() || !isMockReference(providerReference)) {
            return PaymentProviderStatusResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
        }

        return new PaymentProviderStatusResult(
                true,
                PROVIDER_NAME,
                providerReference,
                PaymentStatus.PAID,
                RESULT_CODE_PAID,
                PAID_MESSAGE);
    }

    /**
     * Builds the synthetic redirect URL preserving any existing query
     * string and fragment on the configured success URL. Static because
     * it operates entirely on its arguments.
     */
    private static String buildCheckoutUrl(String successUrl, String reference) {
        String baseUrl = successUrl == null || successUrl.isBlank() ? "/" : successUrl.strip();
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("paymentRef", reference)
                .queryParam("status", "success")
                .build()
                .encode()
                .toUriString();
    }

    private static boolean isMockReference(String providerReference) {
        return providerReference != null && providerReference.strip().startsWith(REFERENCE_PREFIX);
    }
}
