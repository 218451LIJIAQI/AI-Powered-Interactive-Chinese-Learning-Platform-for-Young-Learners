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
