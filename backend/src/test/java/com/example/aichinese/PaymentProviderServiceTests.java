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
 * Unit coverage for {@code PaymentProviderService} — pins the
 * runtime selection: {@code stripe} → Stripe client, {@code mock}
 * with the success toggle on → mock client, otherwise the
 * {@code NoOpPaymentProviderClient} fail-safe.
 */
class PaymentProviderServiceTests {

    @Test
    void selectsNoOpProviderWhenMockSuccessIsDisabled() {
        Fixture fixture = fixture(false, "mock", "", "");

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(client).isSameAs(fixture.noOpClient());
        assertThat(client.providerName()).isEqualTo("no-op");
    }

    @Test
    void defaultsToNoOpProviderWhenProviderIsBlank() {
        Fixture fixture = fixture(true, "", "", "");

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(fixture.paymentProperties().provider()).isEqualTo("no-op");
        assertThat(client).isSameAs(fixture.noOpClient());
    }

    @Test
    void selectsMockProviderOnlyWhenMockSuccessIsEnabled() {
        Fixture fixture = fixture(true, "mock", "", "");

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(client).isSameAs(fixture.mockClient());
        assertThat(client.providerName()).isEqualTo("mock");
    }

    @Test
    void selectsStripeProviderWhenConfiguredEvenIfMockIsEnabled() {
        Fixture fixture = fixture(true, "stripe", "sk_test_example", "whsec_test_example");

        PaymentProviderClient client = fixture.service().getClient();

        assertThat(client).isSameAs(fixture.stripeClient());
        assertThat(client.providerName()).isEqualTo("stripe");
    }

    @Test
    void rejectsNonHttpPaymentRedirectUrls() {
        assertThatThrownBy(() -> new PaymentProperties(
                "javascript:alert(1)",
                "http://localhost:5174/payment/cancel",
                true,
                "mock",
                "https://api.stripe.com",
                "",
                ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment success URL");

        assertThatThrownBy(() -> new PaymentProperties(
                "http://localhost:5174/payment/success",
                "ftp://localhost/cancel",
                true,
                "mock",
                "https://api.stripe.com",
                "",
                ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment cancel URL");
    }

    @Test
    void rejectsUnknownPaymentProvider() {
        assertThatThrownBy(() -> new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                true,
                "stripee",
                "https://api.stripe.com",
                "",
                ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment provider");
    }

    private static Fixture fixture(
            boolean mockSuccessEnabled,
            String provider,
            String stripeApiKey,
            String stripeWebhookSecret) {
        PaymentProperties paymentProperties = new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                mockSuccessEnabled,
                provider,
                "https://api.stripe.com",
                stripeApiKey,
                stripeWebhookSecret);
        MockPaymentProviderClient mockClient = new MockPaymentProviderClient(paymentProperties);
        NoOpPaymentProviderClient noOpClient = new NoOpPaymentProviderClient();
        StripePaymentProviderClient stripeClient = new StripePaymentProviderClient(
                paymentProperties,
                new RestTemplateBuilder());
        return new Fixture(
                paymentProperties,
                mockClient,
                noOpClient,
                stripeClient,
                new PaymentProviderService(paymentProperties, mockClient, noOpClient, stripeClient));
    }

    private record Fixture(
            PaymentProperties paymentProperties,
            MockPaymentProviderClient mockClient,
            NoOpPaymentProviderClient noOpClient,
            StripePaymentProviderClient stripeClient,
            PaymentProviderService service) {
    }
}
