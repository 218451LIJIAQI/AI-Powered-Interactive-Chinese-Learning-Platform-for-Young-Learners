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
        PaymentProperties paymentProperties = new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                false,
                "mock",
                "https://api.stripe.com",
                "",
                "");
        MockPaymentProviderClient mockClient = new MockPaymentProviderClient(paymentProperties);
        NoOpPaymentProviderClient noOpClient = new NoOpPaymentProviderClient();
        StripePaymentProviderClient stripeClient = new StripePaymentProviderClient(paymentProperties, new RestTemplateBuilder());
        PaymentProviderService service = new PaymentProviderService(paymentProperties, mockClient, noOpClient, stripeClient);

        PaymentProviderClient client = service.getClient();

        assertThat(client).isSameAs(noOpClient);
        assertThat(client.providerName()).isEqualTo("no-op");
    }

    @Test
    void defaultsToNoOpProviderWhenProviderIsBlank() {
        PaymentProperties paymentProperties = new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                true,
                "",
                "https://api.stripe.com",
                "",
                "");
        MockPaymentProviderClient mockClient = new MockPaymentProviderClient(paymentProperties);
        NoOpPaymentProviderClient noOpClient = new NoOpPaymentProviderClient();
        StripePaymentProviderClient stripeClient = new StripePaymentProviderClient(paymentProperties, new RestTemplateBuilder());
        PaymentProviderService service = new PaymentProviderService(paymentProperties, mockClient, noOpClient, stripeClient);

        PaymentProviderClient client = service.getClient();

        assertThat(paymentProperties.provider()).isEqualTo("no-op");
        assertThat(client).isSameAs(noOpClient);
    }

    @Test
    void selectsMockProviderOnlyWhenMockSuccessIsEnabled() {
        PaymentProperties paymentProperties = new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                true,
                "mock",
                "https://api.stripe.com",
                "",
                "");
        MockPaymentProviderClient mockClient = new MockPaymentProviderClient(paymentProperties);
        NoOpPaymentProviderClient noOpClient = new NoOpPaymentProviderClient();
        StripePaymentProviderClient stripeClient = new StripePaymentProviderClient(paymentProperties, new RestTemplateBuilder());
        PaymentProviderService service = new PaymentProviderService(paymentProperties, mockClient, noOpClient, stripeClient);

        PaymentProviderClient client = service.getClient();

        assertThat(client).isSameAs(mockClient);
        assertThat(client.providerName()).isEqualTo("mock");
    }

    @Test
    void selectsStripeProviderWhenConfiguredEvenIfMockIsEnabled() {
        PaymentProperties paymentProperties = new PaymentProperties(
                "http://localhost:5174/payment/success",
                "http://localhost:5174/payment/cancel",
                true,
                "stripe",
                "https://api.stripe.com",
                "sk_test_example",
                "whsec_test_example");
        MockPaymentProviderClient mockClient = new MockPaymentProviderClient(paymentProperties);
        NoOpPaymentProviderClient noOpClient = new NoOpPaymentProviderClient();
        StripePaymentProviderClient stripeClient = new StripePaymentProviderClient(paymentProperties, new RestTemplateBuilder());
        PaymentProviderService service = new PaymentProviderService(paymentProperties, mockClient, noOpClient, stripeClient);

        PaymentProviderClient client = service.getClient();

        assertThat(client).isSameAs(stripeClient);
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
}
