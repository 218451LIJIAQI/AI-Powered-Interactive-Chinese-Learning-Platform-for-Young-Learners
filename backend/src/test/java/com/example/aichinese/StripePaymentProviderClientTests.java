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
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.provider.StripePaymentProviderClient;
import com.example.aichinese.service.model.CommerceCheckout;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Unit coverage for {@code StripePaymentProviderClient}. It uses an
 * embedded {@link com.sun.net.httpserver.HttpServer} to stand in for
 * Stripe so the test exercises the real HTTP request shape, signed
 * webhook handling, minor-unit currency translation, and the
 * never-throw contract end-to-end without hitting the real API.
 */
class StripePaymentProviderClientTests {

    private static final RestTemplateBuilder REST_TEMPLATE_BUILDER = new RestTemplateBuilder();

    private static final String AUTHORIZATION = "Authorization";
    private static final String STRIPE_API_URL = "https://api.stripe.com";
    private static final String SUCCESS_URL = "http://localhost:5174/payment/success";
    private static final String CANCEL_URL = "http://localhost:5174/payment/cancel";
    private static final String PAYMENT_SUCCESS_URL = SUCCESS_URL + "?paymentId=123";
    private static final String PAYMENT_CANCEL_URL = CANCEL_URL + "?paymentId=123&cancelled=true";
    private static final String CHECKOUT_SESSIONS_PATH = "/v1/checkout/sessions";
    private static final String SESSION_ID = "cs_test_123";
    private static final String JPY_SESSION_ID = "cs_test_jpy";
    private static final String FALLBACK_SESSION_ID = "cs_test_fallback";
    private static final String MISSING_URL_SESSION_ID = "cs_test_missing_url";
    private static final String INCOMPLETE_SESSION_ID = "cs_test_incomplete";
    private static final String MALFORMED_SESSION_REFERENCE = "../" + SESSION_ID;
    private static final String API_KEY = "sk_test_local";
    private static final String WEBHOOK_SECRET = "whsec_test_local";
    private static final String PROVIDER_STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String PROVIDER_STATUS_COMPLETE_PAID = "COMPLETE:PAID";
    private static final String UNAVAILABLE_MESSAGE =
            "Payment service is currently unavailable. Please try again later.";
    private static final String CHECKOUT_URL = "http://checkout.local/session";
    private static final String CURRENCY_MYR = "MYR";
    private static final String CURRENCY_JPY = "JPY";
    private static final String DEFAULT_ITEM_TITLE = "Mandarin Starter Lesson";
    private static final String STRIPE_TEST_CHECKOUT_URL = "https://checkout.stripe.test/session";
    private static final String ORDER_TOTAL_TITLE = "MandarinSpark order";
    private static final String ITEM_TYPE_LESSON = "LESSON";
    private static final long ITEM_ID = 42L;
    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("29.90");
    private static final BigDecimal FALLBACK_AMOUNT = new BigDecimal("123.45");
    private static final BigDecimal ZERO_DECIMAL_AMOUNT = new BigDecimal("1200");
    private static final BigDecimal OVERFLOW_AMOUNT = new BigDecimal("999999999999999999999999999.99");
    private static final int DEFAULT_QUANTITY = 2;
    private static final int TOO_MANY_LINE_ITEMS = 101;

    @Test
    void createsCheckoutSessionAndMapsPaidStatusThroughStripeRestApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext(CHECKOUT_SESSIONS_PATH, exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst(AUTHORIZATION));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, checkoutSessionJson(SESSION_ID, CHECKOUT_URL));
        });
        server.createContext(CHECKOUT_SESSIONS_PATH + "/" + SESSION_ID, exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst(AUTHORIZATION));
            sendJson(exchange, paidSessionStatusJson(SESSION_ID));
        });
        server.start();
        try {
            StripePaymentProviderClient client = stripeClient(loopbackBaseUrl(server));

            PaymentProviderCheckoutResult checkoutResult = client.createCheckoutSession(defaultCheckout());
            PaymentProviderStatusResult statusResult = client.getPaymentStatus(SESSION_ID);

            String decodedBody = decodedBody(requestBody);
            assertThat(checkoutResult.available()).isTrue();
            assertThat(checkoutResult.sessionReference()).isEqualTo(SESSION_ID);
            assertThat(checkoutResult.checkoutUrl()).isEqualTo(CHECKOUT_URL);
            assertThat(statusResult.paymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(statusResult.providerStatus()).isEqualTo(PROVIDER_STATUS_COMPLETE_PAID);
            assertThat(authorizationHeader.get()).isEqualTo("Bearer " + API_KEY);
            assertThat(decodedBody).contains("mode=payment");
            assertThat(decodedBody).contains("success_url=" + PAYMENT_SUCCESS_URL);
            assertThat(decodedBody).contains("line_items[0][price_data][currency]=myr");
            assertThat(decodedBody).contains("line_items[0][price_data][unit_amount]=2990");
            assertThat(decodedBody).contains("line_items[0][quantity]=" + DEFAULT_QUANTITY);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableWhenStripeApiKeyIsMissing() {
        PaymentProperties properties = new PaymentProperties(
                SUCCESS_URL,
                CANCEL_URL,
                false,
                "stripe",
                STRIPE_API_URL,
                "",
                " ");
        StripePaymentProviderClient client = stripeClient(properties);

        PaymentProviderCheckoutResult result = client.createCheckoutSession(
                checkout(List.of(), BigDecimal.TEN, CURRENCY_MYR));

        assertThat(result.available()).isFalse();
        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.providerStatus()).isEqualTo(PROVIDER_STATUS_UNAVAILABLE);
    }

    @Test
    void returnsUnavailableInsteadOfThrowingWhenAmountOverflowsStripeMinorUnits() {
        StripePaymentProviderClient client = stripeClient(STRIPE_API_URL);

        PaymentProviderCheckoutResult result = client.createCheckoutSession(
                checkout(List.of(), OVERFLOW_AMOUNT, CURRENCY_MYR));

        assertCheckoutUnavailable(result);
        assertThat(result.message()).isEqualTo(UNAVAILABLE_MESSAGE);
    }

    @Test
    void rejectsMalformedProviderReferenceWithoutCallingStripe() throws Exception {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        HttpServer server = localServer();
        server.createContext(CHECKOUT_SESSIONS_PATH + "/" + SESSION_ID, exchange -> {
            called.set(true);
            sendJson(exchange, paidSessionStatusJson(SESSION_ID));
        });
        server.start();
        try {
            StripePaymentProviderClient client = stripeClient(loopbackBaseUrl(server));

            PaymentProviderStatusResult result = client.getPaymentStatus(MALFORMED_SESSION_REFERENCE);

            assertThat(result.available()).isFalse();
            assertThat(result.providerStatus()).isEqualTo(PROVIDER_STATUS_UNAVAILABLE);
            assertThat(called.get()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void treatsIncompleteStripeStatusResponseAsUnavailable() throws Exception {
        HttpServer server = localServer();
        server.createContext(
                CHECKOUT_SESSIONS_PATH + "/" + INCOMPLETE_SESSION_ID,
                exchange -> sendJson(exchange, sessionIdOnlyJson(INCOMPLETE_SESSION_ID)));
        server.start();
        try {
            StripePaymentProviderClient client = stripeClient(loopbackBaseUrl(server));

            PaymentProviderStatusResult result = client.getPaymentStatus(INCOMPLETE_SESSION_ID);

            assertThat(result.available()).isFalse();
            assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.providerStatus()).isEqualTo(PROVIDER_STATUS_UNAVAILABLE);
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
            sendJson(exchange, checkoutSessionJson(JPY_SESSION_ID, STRIPE_TEST_CHECKOUT_URL));
        });
        server.start();
        try {
            StripePaymentProviderClient client = stripeClient(loopbackBaseUrl(server) + "/stripe/");

            PaymentProviderCheckoutResult result = client.createCheckoutSession(checkout(
                    List.of(orderItem("Starter\nLesson\tOne", ZERO_DECIMAL_AMOUNT, 1)),
                    ZERO_DECIMAL_AMOUNT,
                    CURRENCY_JPY));

            String decodedBody = decodedBody(requestBody);
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
        server.createContext(CHECKOUT_SESSIONS_PATH, exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, checkoutSessionJson(FALLBACK_SESSION_ID, STRIPE_TEST_CHECKOUT_URL));
        });
        server.start();
        try {
            StripePaymentProviderClient client = stripeClient(loopbackBaseUrl(server));

            PaymentProviderCheckoutResult result = client.createCheckoutSession(checkout(
                    manyOrderItems(TOO_MANY_LINE_ITEMS),
                    FALLBACK_AMOUNT,
                    CURRENCY_MYR));

            String decodedBody = decodedBody(requestBody);
            assertThat(result.available()).isTrue();
            assertThat(decodedBody).contains("line_items[0][price_data][product_data][name]=" + ORDER_TOTAL_TITLE);
            assertThat(decodedBody).contains("line_items[0][price_data][unit_amount]=12345");
            assertThat(decodedBody).doesNotContain("line_items[1]");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableWhenStripeCheckoutResponseIsIncomplete() throws Exception {
        HttpServer server = localServer();
        server.createContext(
                CHECKOUT_SESSIONS_PATH,
                exchange -> sendJson(exchange, checkoutSessionWithoutUrlJson(MISSING_URL_SESSION_ID)));
        server.start();
        try {
            StripePaymentProviderClient client = stripeClient(loopbackBaseUrl(server));

            PaymentProviderCheckoutResult result = client.createCheckoutSession(defaultCheckout());

            assertCheckoutUnavailable(result);
            assertThat(result.message()).isEqualTo(UNAVAILABLE_MESSAGE);
        } finally {
            server.stop(0);
        }
    }

    private static StripePaymentProviderClient stripeClient(String baseUrl) {
        return stripeClient(stripeProperties(baseUrl));
    }

    private static StripePaymentProviderClient stripeClient(PaymentProperties properties) {
        return new StripePaymentProviderClient(properties, REST_TEMPLATE_BUILDER);
    }

    private static CommerceCheckout defaultCheckout() {
        return checkout(List.of(orderItem()), DEFAULT_AMOUNT, CURRENCY_MYR);
    }

    private static CommerceCheckout checkout(List<OrderItem> items, BigDecimal amount, String currency) {
        StudentProfile student = new StudentProfile();
        Order order = new Order();
        order.setStudent(student);
        order.setTotalAmount(amount);
        order.setCurrencyCode(currency);

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setStudent(student);
        paymentRecord.setOrder(order);
        paymentRecord.setProviderName("stripe");
        paymentRecord.setAmount(amount);
        paymentRecord.setCurrencyCode(currency);

        return new CommerceCheckout(
                student,
                order,
                paymentRecord,
                items,
                amount,
                currency,
                PAYMENT_SUCCESS_URL,
                PAYMENT_CANCEL_URL);
    }

    private static OrderItem orderItem() {
        return orderItem(DEFAULT_ITEM_TITLE, DEFAULT_AMOUNT, DEFAULT_QUANTITY);
    }

    private static OrderItem orderItem(String title, BigDecimal unitPrice, int quantity) {
        OrderItem orderItem = new OrderItem();
        orderItem.setItemType(ITEM_TYPE_LESSON);
        orderItem.setItemId(ITEM_ID);
        orderItem.setItemTitleSnapshot(title);
        orderItem.setUnitPrice(unitPrice);
        orderItem.setQuantity(quantity);
        orderItem.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        return orderItem;
    }

    private static List<OrderItem> manyOrderItems(int count) {
        List<OrderItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            items.add(orderItem("Lesson " + index, BigDecimal.ONE, 1));
        }
        return items;
    }

    private static PaymentProperties stripeProperties(String baseUrl) {
        return new PaymentProperties(
                SUCCESS_URL,
                CANCEL_URL,
                false,
                "stripe",
                baseUrl,
                API_KEY,
                WEBHOOK_SECRET);
    }

    private static HttpServer localServer() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static String loopbackBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String decodedBody(AtomicReference<String> requestBody) {
        return URLDecoder.decode(requestBody.get(), StandardCharsets.UTF_8);
    }

    private static String checkoutSessionJson(String sessionId, String checkoutUrl) {
        return "{\"id\":\"" + sessionId + "\",\"url\":\"" + checkoutUrl + "\",\"status\":\"open\"}";
    }

    private static String paidSessionStatusJson(String sessionId) {
        return "{\"id\":\"" + sessionId + "\",\"status\":\"complete\",\"payment_status\":\"paid\"}";
    }

    private static String checkoutSessionWithoutUrlJson(String sessionId) {
        return "{\"id\":\"" + sessionId + "\",\"status\":\"open\"}";
    }

    private static String sessionIdOnlyJson(String sessionId) {
        return "{\"id\":\"" + sessionId + "\"}";
    }

    private static void assertCheckoutUnavailable(PaymentProviderCheckoutResult result) {
        assertThat(result.available()).isFalse();
        assertThat(result.providerStatus()).isEqualTo(PROVIDER_STATUS_UNAVAILABLE);
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
