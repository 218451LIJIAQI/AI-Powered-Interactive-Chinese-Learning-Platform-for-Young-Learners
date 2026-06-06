package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.provider.MockPaymentProviderClient;
import com.example.aichinese.service.model.CommerceCheckout;

/**
 * Unit coverage for the development-only {@code MockPaymentProviderClient}
 * - pins the query-string assembly correctness (ampersand when
 * {@code successUrl} already has parameters, question mark otherwise)
 * and the deterministic-success contract so dev tests don't have to
 * spin up a real provider.
 */
class MockPaymentProviderClientTests {

    private static final String SUCCESS_URL = "http://localhost:5174/payment/success";
    private static final String CANCEL_URL = "http://localhost:5174/payment/cancel";
    private static final String SUCCESS_URL_WITH_PAYMENT_ID = SUCCESS_URL + "?paymentId=123";
    private static final String CANCEL_URL_WITH_PAYMENT_ID = CANCEL_URL + "?paymentId=123&cancelled=true";
    private static final String MOCK_PROVIDER = "mock";
    private static final String STRIPE_API_BASE_URL = "https://api.stripe.com";
    private static final String EMPTY_PROVIDER_SECRET = "";
    private static final String CURRENCY = "MYR";
    private static final String MOCK_CHECKOUT_PREFIX = SUCCESS_URL_WITH_PAYMENT_ID + "&paymentRef=mock-checkout-";
    private static final String SUCCESS_STATUS_PARAMETER = "&status=success";
    private static final String EXTERNAL_REFERENCE = "cs_test_external";
    private static final String UNAVAILABLE_STATUS = "UNAVAILABLE";

    @Test
    void checkoutUrlAppendsParametersWithAmpersandWhenSuccessUrlAlreadyHasQueryString() {
        MockPaymentProviderClient client = client(true);

        PaymentProviderCheckoutResult result = client.createCheckoutSession(checkout(SUCCESS_URL_WITH_PAYMENT_ID));

        assertThat(result.available()).isTrue();
        assertThat(result.checkoutUrl()).startsWith(MOCK_CHECKOUT_PREFIX);
        assertThat(result.checkoutUrl()).contains(SUCCESS_STATUS_PARAMETER);
        assertThat(result.checkoutUrl()).doesNotContain("??");
        assertThat(result.checkoutUrl()).doesNotContain("?paymentId=123?paymentRef=");
    }

    @Test
    void unavailableWhenMockSuccessIsDisabled() {
        MockPaymentProviderClient client = client(false);

        PaymentProviderCheckoutResult result = client.createCheckoutSession(checkout(SUCCESS_URL_WITH_PAYMENT_ID));

        assertThat(result.available()).isFalse();
        assertThat(result.checkoutUrl()).isNull();
    }

    @Test
    void checkoutUrlPreservesFragmentsWhenAddingMockParameters() {
        MockPaymentProviderClient client = client(true);

        PaymentProviderCheckoutResult result = client.createCheckoutSession(checkout(
                SUCCESS_URL_WITH_PAYMENT_ID + "#receipt",
                CANCEL_URL));

        assertThat(result.checkoutUrl())
                .startsWith(MOCK_CHECKOUT_PREFIX)
                .endsWith(SUCCESS_STATUS_PARAMETER + "#receipt");
    }

    @Test
    void statusIsUnavailableForNonMockReferences() {
        MockPaymentProviderClient client = client(true);

        PaymentProviderStatusResult result = client.getPaymentStatus(EXTERNAL_REFERENCE);

        assertThat(result.available()).isFalse();
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.providerStatus()).isEqualTo(UNAVAILABLE_STATUS);
    }

    private static MockPaymentProviderClient client(boolean mockSuccess) {
        return new MockPaymentProviderClient(new PaymentProperties(
                SUCCESS_URL,
                CANCEL_URL,
                mockSuccess,
                MOCK_PROVIDER,
                STRIPE_API_BASE_URL,
                EMPTY_PROVIDER_SECRET,
                EMPTY_PROVIDER_SECRET));
    }

    private static CommerceCheckout checkout(String successUrl) {
        return checkout(successUrl, CANCEL_URL_WITH_PAYMENT_ID);
    }

    private static CommerceCheckout checkout(String successUrl, String cancelUrl) {
        StudentProfile student = new StudentProfile();
        Order order = new Order();
        order.setStudent(student);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCurrencyCode(CURRENCY);

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setStudent(student);
        paymentRecord.setOrder(order);
        paymentRecord.setProviderName(MOCK_PROVIDER);
        paymentRecord.setAmount(BigDecimal.TEN);
        paymentRecord.setCurrencyCode(CURRENCY);

        return new CommerceCheckout(
                student,
                order,
                paymentRecord,
                List.of(),
                BigDecimal.TEN,
                CURRENCY,
                successUrl,
                cancelUrl);
    }
}
