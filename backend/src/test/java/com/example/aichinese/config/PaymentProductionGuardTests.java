package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit coverage for {@code PaymentProductionGuard} - pins the fail-fast behaviour for
 * payment provider configuration in production: Stripe live-mode requires a
 * {@code sk_live_*} key and the canonical {@code api.stripe.com} base URL; mock
 * provider is refused outside dev/test profiles so a deployment cannot accidentally
 * accept fake payments.
 */
class PaymentProductionGuardTests {

    private static final String PROD_PROFILE = "prod";
    private static final String LOCAL_SUCCESS_URL = "http://localhost:5174/payment/success";
    private static final String LOCAL_CANCEL_URL = "http://localhost:5174/payment/cancel";
    private static final String SUCCESS_URL = "https://app.example.com/payment/success";
    private static final String CANCEL_URL = "https://app.example.com/payment/cancel";
    private static final String STRIPE_PROVIDER = "stripe";
    private static final String NO_OP_PROVIDER = "no-op";
    private static final String STRIPE_API_BASE_URL = "https://api.stripe.com";
    private static final String NON_STRIPE_API_BASE_URL = "https://payments.example.com";
    private static final String NON_HTTPS_STRIPE_API_BASE_URL = "http://stripe.example.test";
    private static final String LIVE_STRIPE_API_KEY = "sk_live_example";
    private static final String TEST_STRIPE_API_KEY = "sk_test_example";
    private static final String PLACEHOLDER_STRIPE_API_KEY = "replace-with-your-stripe-secret-key";
    private static final String LIVE_STRIPE_WEBHOOK_SECRET = "whsec_live_example";
    private static final String PLACEHOLDER_STRIPE_WEBHOOK_SECRET =
            "replace-with-your-stripe-webhook-signing-secret";

    @Test
    void allowsDevProfileToUseNoOpProvider() {
        PaymentProperties paymentProperties = paymentProperties()
                .successUrl(LOCAL_SUCCESS_URL)
                .cancelUrl(LOCAL_CANCEL_URL)
                .provider(NO_OP_PROVIDER)
                .stripeApiKey("")
                .stripeWebhookSecret("")
                .build();

        assertThatCode(() -> new PaymentProductionGuard(new MockEnvironment(), paymentProperties))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfileRequiresStripeProvider() {
        assertProductionRejected(
                paymentProperties().provider(NO_OP_PROVIDER).build(),
                "PAYMENT_PROVIDER");
    }

    @Test
    void prodProfileRejectsMockSuccessMode() {
        assertProductionRejected(
                paymentProperties().mockSuccessEnabled(true).build(),
                "PAYMENT_MOCK_SUCCESS_ENABLED");
    }

    @Test
    void prodProfileRejectsMissingStripeSecret() {
        assertProductionRejected(
                paymentProperties().stripeApiKey("").build(),
                "STRIPE_API_KEY");
    }

    @Test
    void prodProfileRejectsTestModeOrUnknownStripeApiKeys() {
        assertProductionRejected(
                paymentProperties().stripeApiKey(TEST_STRIPE_API_KEY).build(),
                "live-mode Stripe secret key");

        assertProductionRejected(
                paymentProperties().stripeApiKey(PLACEHOLDER_STRIPE_API_KEY).build(),
                "sk_live_ or rk_live_");
    }

    @Test
    void prodProfileRejectsMissingStripeWebhookSecret() {
        assertProductionRejected(
                paymentProperties().stripeWebhookSecret("").build(),
                "STRIPE_WEBHOOK_SECRET");
    }

    @Test
    void prodProfileRejectsMalformedStripeWebhookSecret() {
        assertProductionRejected(
                paymentProperties().stripeWebhookSecret(PLACEHOLDER_STRIPE_WEBHOOK_SECRET).build(),
                "Stripe webhook signing secret");
    }

    @Test
    void prodProfileRejectsLoopbackOrNonHttpsPaymentUrls() {
        assertProductionRejected(
                paymentProperties().successUrl("https://localhost:5174/payment/success").build(),
                "PAYMENT_SUCCESS_URL");

        assertProductionRejected(
                paymentProperties().stripeApiBaseUrl(NON_HTTPS_STRIPE_API_BASE_URL).build(),
                "STRIPE_API_BASE_URL");
    }

    @Test
    void prodProfileRejectsNonStripeApiBaseUrl() {
        assertProductionRejected(
                paymentProperties().stripeApiBaseUrl(NON_STRIPE_API_BASE_URL).build(),
                STRIPE_API_BASE_URL);
    }

    @Test
    void prodProfileAllowsRealStripeConfiguration() {
        assertThatCode(() -> new PaymentProductionGuard(productionEnvironment(), paymentProperties().build()))
                .doesNotThrowAnyException();
    }

    private static void assertProductionRejected(PaymentProperties paymentProperties, String expectedMessage) {
        assertThatThrownBy(() -> new PaymentProductionGuard(productionEnvironment(), paymentProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(PROD_PROFILE);
        return environment;
    }

    private static PaymentPropertiesBuilder paymentProperties() {
        return new PaymentPropertiesBuilder();
    }

    private static final class PaymentPropertiesBuilder {
        private String successUrl = SUCCESS_URL;
        private String cancelUrl = CANCEL_URL;
        private boolean mockSuccessEnabled;
        private String provider = STRIPE_PROVIDER;
        private String stripeApiBaseUrl = STRIPE_API_BASE_URL;
        private String stripeApiKey = LIVE_STRIPE_API_KEY;
        private String stripeWebhookSecret = LIVE_STRIPE_WEBHOOK_SECRET;

        private PaymentPropertiesBuilder successUrl(String successUrl) {
            this.successUrl = successUrl;
            return this;
        }

        private PaymentPropertiesBuilder cancelUrl(String cancelUrl) {
            this.cancelUrl = cancelUrl;
            return this;
        }

        private PaymentPropertiesBuilder mockSuccessEnabled(boolean mockSuccessEnabled) {
            this.mockSuccessEnabled = mockSuccessEnabled;
            return this;
        }

        private PaymentPropertiesBuilder provider(String provider) {
            this.provider = provider;
            return this;
        }

        private PaymentPropertiesBuilder stripeApiBaseUrl(String stripeApiBaseUrl) {
            this.stripeApiBaseUrl = stripeApiBaseUrl;
            return this;
        }

        private PaymentPropertiesBuilder stripeApiKey(String stripeApiKey) {
            this.stripeApiKey = stripeApiKey;
            return this;
        }

        private PaymentPropertiesBuilder stripeWebhookSecret(String stripeWebhookSecret) {
            this.stripeWebhookSecret = stripeWebhookSecret;
            return this;
        }

        private PaymentProperties build() {
            return new PaymentProperties(
                    successUrl,
                    cancelUrl,
                    mockSuccessEnabled,
                    provider,
                    stripeApiBaseUrl,
                    stripeApiKey,
                    stripeWebhookSecret);
        }
    }
}
