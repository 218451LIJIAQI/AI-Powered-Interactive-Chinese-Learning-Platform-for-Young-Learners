package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

class StripePaymentProviderClientTests {

    @Test
    void createsCheckoutSessionAndMapsPaidStatusThroughStripeRestApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
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
                    "http://localhost:" + server.getAddress().getPort(),
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

    private OrderItem orderItem() {
        OrderItem orderItem = new OrderItem();
        orderItem.setItemType("LESSON");
        orderItem.setItemId(42L);
        orderItem.setItemTitleSnapshot("Mandarin Starter Lesson");
        orderItem.setUnitPrice(new BigDecimal("29.90"));
        orderItem.setQuantity(2);
        orderItem.setLineTotal(new BigDecimal("59.80"));
        return orderItem;
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
