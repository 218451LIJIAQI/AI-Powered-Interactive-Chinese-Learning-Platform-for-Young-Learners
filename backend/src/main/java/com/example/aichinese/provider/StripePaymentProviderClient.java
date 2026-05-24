package com.example.aichinese.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.service.model.CommerceCheckout;

/**
 * Real-money implementation of {@link PaymentProviderClient} that talks
 * to Stripe Checkout via the Stripe REST API.
 *
 * <h2>Why a hand-rolled REST client (not the official Stripe SDK)</h2>
 * <p>The platform only uses two Stripe endpoints
 * ({@code POST /v1/checkout/sessions} and
 * {@code GET /v1/checkout/sessions/{id}}); pulling in the full Stripe
 * Java SDK would add ~10 MB of transitive dependencies for two HTTP
 * calls. The form-encoded body shape, line-item indexing pattern, and
 * metadata namespacing are stable Stripe contracts, so a direct
 * {@link RestTemplate} call is both lighter and easier to mock in
 * tests.</p>
 *
 * <h2>Currency / minor-unit handling</h2>
 * <p>Stripe expects amounts in the currency's smallest unit (cents for
 * USD, sen for MYR, etc.) — except for the small set of zero-decimal
 * currencies enumerated in {@link #ZERO_DECIMAL_CURRENCIES} where the
 * unit is the currency itself (JPY, KRW, etc., where there is no
 * sub-unit). {@link #toMinorUnits(BigDecimal, String)} bridges the
 * platform's {@link BigDecimal} amounts to the integer unit Stripe
 * requires, with explicit half-up rounding so the rounded amount never
 * silently differs from what the order shows the buyer.</p>
 *
 * <h2>Endpoint integrity</h2>
 * <p>{@link #endpoint(String)} refuses to issue requests over
 * {@code http} unless the host is loopback (used by integration tests
 * that point Stripe at a local mock). Production traffic must be HTTPS
 * to api.stripe.com — this is enforced by both
 * {@link com.example.aichinese.config.PaymentProductionGuard}
 * (startup) and this method (runtime), giving defence-in-depth against
 * a configuration change that bypasses the startup gate.</p>
 *
 * <h2>Failure handling</h2>
 * <p>Both {@link #createCheckoutSession} and {@link #getPaymentStatus}
 * catch every {@link RestClientException} / {@link IllegalArgumentException}
 * and translate it to an unavailable result with the canonical
 * {@link #PAYMENT_UNAVAILABLE_MESSAGE}. The exception's message is
 * logged at {@code WARN} but never propagated to the client, so a
 * Stripe-side error message can never leak to a learner's browser.</p>
 *
 * <h2>Metadata round-trip</h2>
 * <p>The metadata fields written via
 * {@link #addMetadata(MultiValueMap, CommerceCheckout)} are read back by
 * {@code StripeWebhookService} when Stripe POSTs a
 * {@code checkout.session.completed} event, allowing the platform to
 * reconcile the inbound webhook with the original order without
 * scanning the entire {@code payment_records} table.</p>
 */
@Component
public class StripePaymentProviderClient implements PaymentProviderClient {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProviderClient.class);
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");

    private final PaymentProperties paymentProperties;
    private final RestTemplate restTemplate;

    public StripePaymentProviderClient(PaymentProperties paymentProperties, RestTemplateBuilder restTemplateBuilder) {
        this.paymentProperties = paymentProperties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public String providerName() {
        return "stripe";
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        if (!configured()) {
            return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("mode", "payment");
        body.add("success_url", checkout.successUrl());
        body.add("cancel_url", checkout.cancelUrl());
        if (checkout.paymentRecord() != null && checkout.paymentRecord().getId() != null) {
            body.add("client_reference_id", String.valueOf(checkout.paymentRecord().getId()));
        }
        addMetadata(body, checkout);
        addLineItems(body, checkout);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint("/v1/checkout/sessions"),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers(MediaType.APPLICATION_FORM_URLENCODED)),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> responseBody = response.getBody();
            String sessionId = stringValue(responseBody, "id");
            String checkoutUrl = stringValue(responseBody, "url");
            String status = normalizeStatus(stringValue(responseBody, "status"));
            if (sessionId == null || checkoutUrl == null) {
                return PaymentProviderCheckoutResult.unavailable(providerName(), "Stripe checkout session response was incomplete.");
            }
            return new PaymentProviderCheckoutResult(true, providerName(), sessionId, checkoutUrl, status, "Stripe checkout session created successfully.");
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Stripe checkout session creation failed: {}", exception.getMessage());
            return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public PaymentProviderStatusResult getPaymentStatus(String providerReference) {
        if (!configured() || providerReference == null || providerReference.isBlank()) {
            return PaymentProviderStatusResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }

        try {
            String encodedReference = UriUtils.encodePathSegment(providerReference.trim(), java.nio.charset.StandardCharsets.UTF_8);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint("/v1/checkout/sessions/" + encodedReference),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(null)),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> responseBody = response.getBody();
            String sessionId = stringValue(responseBody, "id");
            String sessionStatus = normalizeStatus(stringValue(responseBody, "status"));
            String paymentStatus = normalizeStatus(stringValue(responseBody, "payment_status"));
            return new PaymentProviderStatusResult(
                    true,
                    providerName(),
                    sessionId == null ? providerReference : sessionId,
                    mapPaymentStatus(sessionStatus, paymentStatus),
                    sessionStatus + ":" + paymentStatus,
                    "Stripe payment status retrieved successfully.");
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Stripe payment status retrieval failed: {}", exception.getMessage());
            return PaymentProviderStatusResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }
    }

    private boolean configured() {
        return paymentProperties.stripeApiKey() != null && !paymentProperties.stripeApiKey().isBlank();
    }

    private HttpHeaders headers(MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        headers.setBearerAuth(paymentProperties.stripeApiKey());
        return headers;
    }

    private String endpoint(String path) {
        String baseUrl = paymentProperties.stripeApiBaseUrl().endsWith("/")
                ? paymentProperties.stripeApiBaseUrl().substring(0, paymentProperties.stripeApiBaseUrl().length() - 1)
                : paymentProperties.stripeApiBaseUrl();
        URI uri = URI.create(baseUrl + path);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !("http".equals(scheme) && isLoopbackHost(uri.getHost()))) {
            throw new IllegalArgumentException("Stripe API base URL must use HTTPS unless it targets localhost.");
        }
        return uri.toString();
    }

    private boolean isLoopbackHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "::1".equals(normalized) || "[::1]".equals(normalized);
    }

    private void addMetadata(MultiValueMap<String, String> body, CommerceCheckout checkout) {
        if (checkout.order() != null) {
            if (checkout.order().getId() != null) {
                body.add("metadata[order_id]", String.valueOf(checkout.order().getId()));
            }
            if (checkout.order().getOrderNumber() != null && !checkout.order().getOrderNumber().isBlank()) {
                body.add("metadata[order_number]", checkout.order().getOrderNumber());
            }
        }
        if (checkout.paymentRecord() != null && checkout.paymentRecord().getId() != null) {
            body.add("metadata[payment_id]", String.valueOf(checkout.paymentRecord().getId()));
        }
        if (checkout.student() != null && checkout.student().getId() != null) {
            body.add("metadata[student_id]", String.valueOf(checkout.student().getId()));
        }
    }

    private void addLineItems(MultiValueMap<String, String> body, CommerceCheckout checkout) {
        List<OrderItem> items = checkout.orderItems() == null ? List.of() : checkout.orderItems();
        if (items.isEmpty()) {
            body.add("line_items[0][price_data][currency]", currency(checkout.currencyCode()));
            body.add("line_items[0][price_data][product_data][name]", "MandarinSpark order");
            body.add("line_items[0][price_data][unit_amount]", String.valueOf(toMinorUnits(checkout.totalAmount(), checkout.currencyCode())));
            body.add("line_items[0][quantity]", "1");
            return;
        }

        for (int index = 0; index < items.size(); index++) {
            OrderItem item = items.get(index);
            body.add("line_items[" + index + "][price_data][currency]", currency(checkout.currencyCode()));
            body.add("line_items[" + index + "][price_data][product_data][name]", productName(item));
            body.add("line_items[" + index + "][price_data][unit_amount]", String.valueOf(toMinorUnits(item.getUnitPrice(), checkout.currencyCode())));
            body.add("line_items[" + index + "][quantity]", String.valueOf(item.getQuantity() == null || item.getQuantity() < 1 ? 1 : item.getQuantity()));
        }
    }

    private String currency(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank() ? "myr" : currencyCode.trim().toLowerCase(Locale.ROOT);
    }

    private String productName(OrderItem item) {
        String title = item.getItemTitleSnapshot();
        if (title == null || title.isBlank()) {
            return item.getItemType() == null || item.getItemType().isBlank() ? "MandarinSpark course" : item.getItemType() + " " + item.getItemId();
        }
        return title.trim();
    }

    private long toMinorUnits(BigDecimal amount, String currencyCode) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        String normalizedCurrency = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        BigDecimal multiplier = ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency) ? BigDecimal.ONE : BigDecimal.valueOf(100);
        return safeAmount.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private PaymentStatus mapPaymentStatus(String sessionStatus, String paymentStatus) {
        if ("PAID".equals(paymentStatus) || "NO_PAYMENT_REQUIRED".equals(paymentStatus)) {
            return PaymentStatus.PAID;
        }
        if ("EXPIRED".equals(sessionStatus)) {
            return PaymentStatus.CANCELLED;
        }
        if ("COMPLETE".equals(sessionStatus) && "UNPAID".equals(paymentStatus)) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
    }

    private String normalizeStatus(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String stringValue(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
