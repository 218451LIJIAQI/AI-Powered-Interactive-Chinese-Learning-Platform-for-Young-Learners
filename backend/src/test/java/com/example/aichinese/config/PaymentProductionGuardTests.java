package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PaymentProductionGuardTests {

    @Test
    void allowsDevProfileToUseNoOpProvider() {
        PaymentProperties paymentProperties = new PaymentProperties(
                "http://localhost:5173/payment/success",
                "http://localhost:5173/payment/cancel",
                false,
                "no-op",
                "https://api.stripe.com",
                "",
                "");

        assertThatCode(() -> new PaymentProductionGuard(new MockEnvironment(), paymentProperties))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfileRequiresStripeProvider() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties paymentProperties = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "no-op",
                "https://api.stripe.com",
                "sk_live_example",
                "whsec_live_example");

        assertThatThrownBy(() -> new PaymentProductionGuard(environment, paymentProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_PROVIDER");
    }

    @Test
    void prodProfileRejectsMockSuccessMode() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties paymentProperties = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                true,
                "stripe",
                "https://api.stripe.com",
                "sk_live_example",
                "whsec_live_example");

        assertThatThrownBy(() -> new PaymentProductionGuard(environment, paymentProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_MOCK_SUCCESS_ENABLED");
    }

    @Test
    void prodProfileRejectsMissingStripeSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties paymentProperties = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "",
                "whsec_live_example");

        assertThatThrownBy(() -> new PaymentProductionGuard(environment, paymentProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_API_KEY");
    }

    @Test
    void prodProfileRejectsMissingStripeWebhookSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties paymentProperties = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "sk_live_example",
                "");

        assertThatThrownBy(() -> new PaymentProductionGuard(environment, paymentProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    @Test
    void prodProfileRejectsLoopbackOrNonHttpsPaymentUrls() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        PaymentProperties localhostUrls = new PaymentProperties(
                "https://localhost:5173/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "sk_live_example",
                "whsec_live_example");
        assertThatThrownBy(() -> new PaymentProductionGuard(environment, localhostUrls))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_SUCCESS_URL");

        PaymentProperties nonHttpsStripeEndpoint = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "http://stripe.example.test",
                "sk_live_example",
                "whsec_live_example");
        assertThatThrownBy(() -> new PaymentProductionGuard(environment, nonHttpsStripeEndpoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_API_BASE_URL");
    }

    @Test
    void prodProfileAllowsRealStripeConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties paymentProperties = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "sk_live_example",
                "whsec_live_example");

        assertThatCode(() -> new PaymentProductionGuard(environment, paymentProperties))
                .doesNotThrowAnyException();
    }
}
