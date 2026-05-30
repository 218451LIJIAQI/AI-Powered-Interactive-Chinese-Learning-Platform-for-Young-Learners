package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit coverage for {@code PaymentProductionGuard} — pins the
 * fail-fast behaviour for payment provider configuration in
 * production: Stripe live-mode requires a {@code sk_live_*} key and
 * the canonical {@code api.stripe.com} base URL; mock provider is
 * refused outside dev/test profiles so a deployment cannot
 * accidentally accept fake payments.
 */
class PaymentProductionGuardTests {

    @Test
    void allowsDevProfileToUseNoOpProvider() {
        PaymentProperties paymentProperties = new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
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
    void prodProfileRejectsTestModeOrUnknownStripeApiKeys() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        PaymentProperties testModeKey = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "sk_test_example",
                "whsec_live_example");
        assertThatThrownBy(() -> new PaymentProductionGuard(environment, testModeKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("live-mode Stripe secret key");

        PaymentProperties unknownKeyPrefix = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "replace-with-your-stripe-secret-key",
                "whsec_live_example");
        assertThatThrownBy(() -> new PaymentProductionGuard(environment, unknownKeyPrefix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sk_live_ or rk_live_");
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
    void prodProfileRejectsMalformedStripeWebhookSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties paymentProperties = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "sk_live_example",
                "replace-with-your-stripe-webhook-signing-secret");

        assertThatThrownBy(() -> new PaymentProductionGuard(environment, paymentProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stripe webhook signing secret");
    }

    @Test
    void prodProfileRejectsLoopbackOrNonHttpsPaymentUrls() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        PaymentProperties localhostUrls = new PaymentProperties(
                "https://localhost:5174/payment/success",
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
    void prodProfileRejectsNonStripeApiBaseUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties paymentProperties = new PaymentProperties(
                "https://app.example.com/payment/success",
                "https://app.example.com/payment/cancel",
                false,
                "stripe",
                "https://payments.example.com",
                "sk_live_example",
                "whsec_live_example");

        assertThatThrownBy(() -> new PaymentProductionGuard(environment, paymentProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https://api.stripe.com");
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
