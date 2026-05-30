package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.provider.MockPaymentProviderClient;
import com.example.aichinese.service.model.CommerceCheckout;

/**
 * Unit coverage for the development-only {@code MockPaymentProviderClient}
 * — pins the query-string assembly correctness (ampersand when
 * {@code successUrl} already has parameters, question mark otherwise)
 * and the deterministic-success contract so dev tests don't have to
 * spin up a real provider.
 */
class MockPaymentProviderClientTests {

    @Test
    void checkoutUrlAppendsParametersWithAmpersandWhenSuccessUrlAlreadyHasQueryString() {
        MockPaymentProviderClient client = new MockPaymentProviderClient(new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                true,
                "mock",
                "https://api.stripe.com",
                "",
                ""));

        PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                null,
                null,
                null,
                List.of(),
                BigDecimal.TEN,
                "MYR",
                "http://localhost:5174/payment/success?paymentId=123",
                "http://localhost:5174/payment/cancel?paymentId=123&cancelled=true"));

        assertThat(result.available()).isTrue();
        assertThat(result.checkoutUrl()).startsWith("http://localhost:5174/payment/success?paymentId=123&paymentRef=mock-checkout-");
        assertThat(result.checkoutUrl()).contains("&status=success");
        assertThat(result.checkoutUrl()).doesNotContain("??");
        assertThat(result.checkoutUrl()).doesNotContain("?paymentId=123?paymentRef=");
    }

    @Test
    void unavailableWhenMockSuccessIsDisabled() {
        MockPaymentProviderClient client = new MockPaymentProviderClient(new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                false,
                "mock",
                "https://api.stripe.com",
                "",
                ""));

        PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                null,
                null,
                null,
                List.of(),
                BigDecimal.TEN,
                "MYR",
                "http://localhost:5174/payment/success?paymentId=123",
                "http://localhost:5174/payment/cancel?paymentId=123&cancelled=true"));

        assertThat(result.available()).isFalse();
        assertThat(result.checkoutUrl()).isNull();
    }

    @Test
    void checkoutUrlPreservesFragmentsWhenAddingMockParameters() {
        MockPaymentProviderClient client = new MockPaymentProviderClient(new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                true,
                "mock",
                "https://api.stripe.com",
                "",
                ""));

        PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                null,
                null,
                null,
                List.of(),
                BigDecimal.TEN,
                "MYR",
                "http://localhost:5174/payment/success?paymentId=123#receipt",
                "http://localhost:5174/payment/cancel"));

        assertThat(result.checkoutUrl())
                .startsWith("http://localhost:5174/payment/success?paymentId=123&paymentRef=mock-checkout-")
                .endsWith("&status=success#receipt");
    }

    @Test
    void statusIsUnavailableForNonMockReferences() {
        MockPaymentProviderClient client = new MockPaymentProviderClient(new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                true,
                "mock",
                "https://api.stripe.com",
                "",
                ""));

        PaymentProviderStatusResult result = client.getPaymentStatus("cs_test_external");

        assertThat(result.available()).isFalse();
        assertThat(result.paymentStatus()).isEqualTo(com.example.aichinese.entity.enums.PaymentStatus.FAILED);
        assertThat(result.providerStatus()).isEqualTo("UNAVAILABLE");
    }
}
