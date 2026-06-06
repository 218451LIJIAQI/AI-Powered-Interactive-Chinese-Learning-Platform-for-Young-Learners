package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.provider.MockPaymentProviderClient;
import com.example.aichinese.provider.NoOpPaymentProviderClient;
import com.example.aichinese.provider.PaymentProviderClient;
import com.example.aichinese.provider.StripePaymentProviderClient;
import com.example.aichinese.service.PaymentProviderService;

/**
 * Unit coverage for {@code PaymentProviderService} - pins the
 * runtime selection: {@code stripe} -> Stripe client, {@code mock}
 * with the success toggle on -> mock client, otherwise the
 * {@code NoOpPaymentProviderClient} fail-safe.
 */
class PaymentProviderServiceTests {

    private static final String SUCCESS_URL = "http://localhost:5174/payment/success";
    private static final String CANCEL_URL = "http://localhost:5174/payment/cancel";
    private static final String JAVASCRIPT_SUCCESS_URL = "javascript:alert(1)";
    private static final String FTP_CANCEL_URL = "ftp://localhost/cancel";
    private static final String STRIPE_API_BASE_URL = "https://api.stripe.com";
    private static final String PROVIDER_NO_OP = "no-op";
    private static final String PROVIDER_MOCK = "mock";
    private static final String PROVIDER_STRIPE = "stripe";
    private static final String PROVIDER_UNKNOWN = "stripee";
    private static final String EMPTY_SECRET = "";
    private static final String STRIPE_API_KEY = "sk_test_example";
    private static final String STRIPE_WEBHOOK_SECRET = "whsec_test_example";

    private static final RestTemplateBuilder REST_TEMPLATE_BUILDER = new RestTemplateBuilder();

    @Test
    void selectsNoOpProviderWhenMockSuccessIsDisabled() {
        Fixture fixture = fixture(false, PROVIDER_MOCK, EMPTY_SECRET, EMPTY_SECRET);

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(client).isSameAs(fixture.noOpClient());
        assertThat(client.providerName()).isEqualTo(PROVIDER_NO_OP);
    }

    @Test
    void defaultsToNoOpProviderWhenProviderIsBlank() {
        Fixture fixture = fixture(true, EMPTY_SECRET, EMPTY_SECRET, EMPTY_SECRET);

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(fixture.paymentProperties().provider()).isEqualTo(PROVIDER_NO_OP);
        assertThat(client).isSameAs(fixture.noOpClient());
    }

    @Test
    void selectsMockProviderOnlyWhenMockSuccessIsEnabled() {
        Fixture fixture = fixture(true, PROVIDER_MOCK, EMPTY_SECRET, EMPTY_SECRET);

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(client).isSameAs(fixture.mockClient());
        assertThat(client.providerName()).isEqualTo(PROVIDER_MOCK);
    }

    @Test
    void selectsStripeProviderWhenConfiguredEvenIfMockIsEnabled() {
        Fixture fixture = fixture(true, PROVIDER_STRIPE, STRIPE_API_KEY, STRIPE_WEBHOOK_SECRET);

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(client).isSameAs(fixture.stripeClient());
        assertThat(client.providerName()).isEqualTo(PROVIDER_STRIPE);
    }

    @Test
    void rejectsNonHttpPaymentRedirectUrls() {
        assertThatThrownBy(() -> paymentProperties(
                JAVASCRIPT_SUCCESS_URL,
                CANCEL_URL,
                true,
                PROVIDER_MOCK,
                EMPTY_SECRET,
                EMPTY_SECRET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment success URL");

        assertThatThrownBy(() -> paymentProperties(
                SUCCESS_URL,
                FTP_CANCEL_URL,
                true,
                PROVIDER_MOCK,
                EMPTY_SECRET,
                EMPTY_SECRET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment cancel URL");
    }

    @Test
    void rejectsUnknownPaymentProvider() {
        assertThatThrownBy(() -> paymentProperties(
                SUCCESS_URL,
                CANCEL_URL,
                true,
                PROVIDER_UNKNOWN,
                EMPTY_SECRET,
                EMPTY_SECRET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment provider");
    }

    private static Fixture fixture(
            boolean mockSuccessEnabled,
            String provider,
            String stripeApiKey,
            String stripeWebhookSecret) {
        PaymentProperties paymentProperties = paymentProperties(
                SUCCESS_URL,
                CANCEL_URL,
                mockSuccessEnabled,
                provider,
                stripeApiKey,
                stripeWebhookSecret);
        MockPaymentProviderClient mockClient = new MockPaymentProviderClient(paymentProperties);
        NoOpPaymentProviderClient noOpClient = new NoOpPaymentProviderClient();
        StripePaymentProviderClient stripeClient = new StripePaymentProviderClient(
                paymentProperties,
                REST_TEMPLATE_BUILDER);
        return new Fixture(
                paymentProperties,
                mockClient,
                noOpClient,
                stripeClient,
                new PaymentProviderService(paymentProperties, mockClient, noOpClient, stripeClient));
    }

    private static PaymentProperties paymentProperties(
            String successUrl,
            String cancelUrl,
            boolean mockSuccessEnabled,
            String provider,
            String stripeApiKey,
            String stripeWebhookSecret) {
        return new PaymentProperties(
                successUrl,
                cancelUrl,
                mockSuccessEnabled,
                provider,
                STRIPE_API_BASE_URL,
                stripeApiKey,
                stripeWebhookSecret);
    }

    private record Fixture(
            PaymentProperties paymentProperties,
            MockPaymentProviderClient mockClient,
            NoOpPaymentProviderClient noOpClient,
            StripePaymentProviderClient stripeClient,
            PaymentProviderService service) {
    }
}
