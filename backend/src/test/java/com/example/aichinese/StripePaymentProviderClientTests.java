package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.provider.StripePaymentProviderClient;
import com.example.aichinese.service.model.CommerceCheckout;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Unit coverage for {@code StripePaymentProviderClient} — uses an
 * embedded {@link com.sun.net.httpserver.HttpServer} to stand in for
 * Stripe so the test exercises the real HTTP request shape, signed
 * webhook handling, minor-unit currency translation, and the
 * never-throw contract end-to-end without hitting the real API.
 */
class StripePaymentProviderClientTests {

    @Test
    void createsCheckoutSessionAndMapsPaidStatusThroughStripeRestApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext("/v1/checkout/sessions", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, "{\"id\":\"cs_test_123\",\"url\":\"http://checkout.local/session\",\"status\":\"open\"}");
        });
        server.createContext("/v1/checkout/sessions/cs_test_123", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, "{\"id\":\"cs_test_123\",\"status\":\"complete\",\"payment_status\":\"paid\"}");
        });
        server.start();
        try {
            PaymentProperties properties = new PaymentProperties(
                    "http://localhost:5173/payment/success",
                    "http://localhost:5173/payment/cancel",
                    false,
                    "stripe",
                    loopbackBaseUrl(server),
                    "sk_test_local",
                    "whsec_test_local");
            StripePaymentProviderClient client = new StripePaymentProviderClient(properties, new RestTemplateBuilder());

            PaymentProviderCheckoutResult checkoutResult = client.createCheckoutSession(new CommerceCheckout(
                    null,
                    null,
                    null,
                    List.of(orderItem()),
                    new BigDecimal("29.90"),
                    "MYR",
                    "http://localhost:5173/payment/success?paymentId=123",
                    "http://localhost:5173/payment/cancel?paymentId=123&cancelled=true"));
            PaymentProviderStatusResult statusResult = client.getPaymentStatus("cs_test_123");

            String decodedBody = URLDecoder.decode(requestBody.get(), StandardCharsets.UTF_8);
            assertThat(checkoutResult.available()).isTrue();
            assertThat(checkoutResult.sessionReference()).isEqualTo("cs_test_123");
            assertThat(checkoutResult.checkoutUrl()).isEqualTo("http://checkout.local/session");
            assertThat(statusResult.paymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(statusResult.providerStatus()).isEqualTo("COMPLETE:PAID");
            assertThat(authorizationHeader.get()).isEqualTo("Bearer sk_test_local");
            assertThat(decodedBody).contains("mode=payment");
            assertThat(decodedBody).contains("success_url=http://localhost:5173/payment/success?paymentId=123");
            assertThat(decodedBody).contains("line_items[0][price_data][currency]=myr");
            assertThat(decodedBody).contains("line_items[0][price_data][unit_amount]=2990");
            assertThat(decodedBody).contains("line_items[0][quantity]=2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableWhenStripeApiKeyIsMissing() {
        PaymentProperties properties = new PaymentProperties(
                "http://localhost:5173/payment/success",
                "http://localhost:5173/payment/cancel",
                false,
                "stripe",
                "https://api.stripe.com",
                "",
                " ");
        StripePaymentProviderClient client = new StripePaymentProviderClient(properties, new RestTemplateBuilder());

        PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                null,
                null,
                null,
                List.of(),
                BigDecimal.TEN,
                "MYR",
                "http://localhost:5173/payment/success?paymentId=123",
                "http://localhost:5173/payment/cancel?paymentId=123&cancelled=true"));

        assertThat(result.available()).isFalse();
        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.providerStatus()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void returnsUnavailableInsteadOfThrowingWhenAmountOverflowsStripeMinorUnits() {
        StripePaymentProviderClient client = new StripePaymentProviderClient(stripeProperties("https://api.stripe.com"), new RestTemplateBuilder());

        PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                null,
                null,
                null,
                List.of(),
                new BigDecimal("999999999999999999999999999.99"),
                "MYR",
                "http://localhost:5173/payment/success?paymentId=123",
                "http://localhost:5173/payment/cancel?paymentId=123&cancelled=true"));

        assertThat(result.available()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("UNAVAILABLE");
        assertThat(result.message()).isEqualTo("Payment service is currently unavailable. Please try again later.");
    }

    @Test
    void rejectsMalformedProviderReferenceWithoutCallingStripe() throws Exception {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        HttpServer server = localServer();
        server.createContext("/v1/checkout/sessions/cs_test_123", exchange -> {
            called.set(true);
            sendJson(exchange, "{\"id\":\"cs_test_123\",\"status\":\"complete\",\"payment_status\":\"paid\"}");
        });
        server.start();
        try {
            StripePaymentProviderClient client = new StripePaymentProviderClient(
                    stripeProperties(loopbackBaseUrl(server)),
                    new RestTemplateBuilder());

            PaymentProviderStatusResult result = client.getPaymentStatus("../cs_test_123");

            assertThat(result.available()).isFalse();
            assertThat(result.providerStatus()).isEqualTo("UNAVAILABLE");
            assertThat(called.get()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void treatsIncompleteStripeStatusResponseAsUnavailable() throws Exception {
        HttpServer server = localServer();
        server.createContext("/v1/checkout/sessions/cs_test_incomplete", exchange -> sendJson(exchange, "{\"id\":\"cs_test_incomplete\"}"));
        server.start();
        try {
            StripePaymentProviderClient client = new StripePaymentProviderClient(
                    stripeProperties(loopbackBaseUrl(server)),
                    new RestTemplateBuilder());

            PaymentProviderStatusResult result = client.getPaymentStatus("cs_test_incomplete");

            assertThat(result.available()).isFalse();
            assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.providerStatus()).isEqualTo("UNAVAILABLE");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesZeroDecimalCurrencyMinorUnitsAndSanitizesProductNames() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext("/stripe/v1/checkout/sessions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, "{\"id\":\"cs_test_jpy\",\"url\":\"https://checkout.stripe.test/session\",\"status\":\"open\"}");
        });
        server.start();
        try {
            StripePaymentProviderClient client = new StripePaymentProviderClient(
                    stripeProperties(loopbackBaseUrl(server) + "/stripe/"),
                    new RestTemplateBuilder());

            PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                    null,
                    null,
                    null,
                    List.of(orderItem("Starter\nLesson\tOne", new BigDecimal("1200"), 1)),
                    new BigDecimal("1200"),
                    "JPY",
                    "http://localhost:5173/payment/success?paymentId=123",
                    "http://localhost:5173/payment/cancel?paymentId=123&cancelled=true"));

            String decodedBody = URLDecoder.decode(requestBody.get(), StandardCharsets.UTF_8);
            assertThat(result.available()).isTrue();
            assertThat(decodedBody).contains("line_items[0][price_data][currency]=jpy");
            assertThat(decodedBody).contains("line_items[0][price_data][unit_amount]=1200");
            assertThat(decodedBody).contains("line_items[0][price_data][product_data][name]=Starter Lesson One");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void collapsesUnsafeOrOversizedLineItemsToSingleOrderTotalLineItem() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext("/v1/checkout/sessions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, "{\"id\":\"cs_test_fallback\",\"url\":\"https://checkout.stripe.test/session\",\"status\":\"open\"}");
        });
        server.start();
        try {
            StripePaymentProviderClient client = new StripePaymentProviderClient(
                    stripeProperties(loopbackBaseUrl(server)),
                    new RestTemplateBuilder());

            PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                    null,
                    null,
                    null,
                    manyOrderItems(101),
                    new BigDecimal("123.45"),
                    "MYR",
                    "http://localhost:5173/payment/success?paymentId=123",
                    "http://localhost:5173/payment/cancel?paymentId=123&cancelled=true"));

            String decodedBody = URLDecoder.decode(requestBody.get(), StandardCharsets.UTF_8);
            assertThat(result.available()).isTrue();
            assertThat(decodedBody).contains("line_items[0][price_data][product_data][name]=MandarinSpark order");
            assertThat(decodedBody).contains("line_items[0][price_data][unit_amount]=12345");
            assertThat(decodedBody).doesNotContain("line_items[1]");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableWhenStripeCheckoutResponseIsIncomplete() throws Exception {
        HttpServer server = localServer();
        server.createContext("/v1/checkout/sessions", exchange -> sendJson(exchange, "{\"id\":\"cs_test_missing_url\",\"status\":\"open\"}"));
        server.start();
        try {
            StripePaymentProviderClient client = new StripePaymentProviderClient(
                    stripeProperties(loopbackBaseUrl(server)),
                    new RestTemplateBuilder());

            PaymentProviderCheckoutResult result = client.createCheckoutSession(new CommerceCheckout(
                    null,
                    null,
                    null,
                    List.of(orderItem()),
                    new BigDecimal("29.90"),
                    "MYR",
                    "http://localhost:5173/payment/success?paymentId=123",
                    "http://localhost:5173/payment/cancel?paymentId=123&cancelled=true"));

            assertThat(result.available()).isFalse();
            assertThat(result.message()).isEqualTo("Payment service is currently unavailable. Please try again later.");
            assertThat(result.providerStatus()).isEqualTo("UNAVAILABLE");
        } finally {
            server.stop(0);
        }
    }

    private OrderItem orderItem() {
        return orderItem("Mandarin Starter Lesson", new BigDecimal("29.90"), 2);
    }

    private OrderItem orderItem(String title, BigDecimal unitPrice, int quantity) {
        OrderItem orderItem = new OrderItem();
        orderItem.setItemType("LESSON");
        orderItem.setItemId(42L);
        orderItem.setItemTitleSnapshot(title);
        orderItem.setUnitPrice(unitPrice);
        orderItem.setQuantity(quantity);
        orderItem.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        return orderItem;
    }

    private List<OrderItem> manyOrderItems(int count) {
        List<OrderItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            items.add(orderItem("Lesson " + index, BigDecimal.ONE, 1));
        }
        return items;
    }

    private PaymentProperties stripeProperties(String baseUrl) {
        return new PaymentProperties(
                "http://localhost:5173/payment/success",
                "http://localhost:5173/payment/cancel",
                false,
                "stripe",
                baseUrl,
                "sk_test_local",
                "whsec_test_local");
    }

    private HttpServer localServer() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private String loopbackBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
