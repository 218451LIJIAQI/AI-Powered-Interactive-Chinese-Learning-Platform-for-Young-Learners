package com.example.aichinese.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.service.model.CommerceCheckout;

@Component
public class StripePaymentProviderClient implements PaymentProviderClient {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProviderClient.class);

    private static final String PROVIDER_NAME = "stripe";
    private static final String DEFAULT_CURRENCY = "myr";
    private static final String DEFAULT_PRODUCT_NAME = "MandarinSpark course";
    private static final String DEFAULT_FALLBACK_PRODUCT_NAME = "MandarinSpark order";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final BigDecimal MINOR_UNIT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final int MAX_CHECKOUT_LINE_ITEMS = 100;
    private static final int MAX_PRODUCT_NAME_LENGTH = 200;
    private static final int MAX_METADATA_VALUE_LENGTH = 500;
    private static final int MAX_PROVIDER_REFERENCE_LENGTH = 255;
    private static final int MAX_REDIRECT_URL_LENGTH = 2048;
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Za-z]{3}$");
    private static final Pattern PROVIDER_REFERENCE_PATTERN = Pattern.compile("^cs_[A-Za-z0-9_]+$");

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");

    private final PaymentProperties paymentProperties;
    private final RestTemplate restTemplate;

    public StripePaymentProviderClient(PaymentProperties paymentProperties, RestTemplateBuilder restTemplateBuilder) {
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
        this.restTemplate = Objects.requireNonNull(restTemplateBuilder, "restTemplateBuilder")
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .build();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        if (!configured() || checkout == null) {
            return PaymentProviderCheckoutResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
        }

        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("mode", "payment");
            body.add("success_url", requireRedirectUrl(checkout.successUrl(), "Stripe success URL"));
            body.add("cancel_url", requireRedirectUrl(checkout.cancelUrl(), "Stripe cancel URL"));
            if (checkout.paymentRecord() != null && checkout.paymentRecord().getId() != null) {
                body.add("client_reference_id", String.valueOf(checkout.paymentRecord().getId()));
            }
            addMetadata(body, checkout);
            addLineItems(body, checkout);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint("/v1/checkout/sessions"),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers(MediaType.APPLICATION_FORM_URLENCODED)),
                    new ParameterizedTypeReference<>() {
                    });
            Map<String, Object> responseBody = response.getBody();
            String sessionId = stringValue(responseBody, "id");
            String checkoutUrl = stringValue(responseBody, "url");
            String status = normalizeStatus(stringValue(responseBody, "status"));
            if (sessionId == null || checkoutUrl == null) {
                log.warn("Stripe checkout session creation returned an incomplete provider response.");
                return PaymentProviderCheckoutResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
            }

            return new PaymentProviderCheckoutResult(
                    true,
                    PROVIDER_NAME,
                    sessionId,
                    checkoutUrl,
                    status,
                    "Stripe checkout session created successfully.");
        } catch (RuntimeException exception) {
            logProviderFailure("Stripe checkout session creation failed", exception);
            return PaymentProviderCheckoutResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public PaymentProviderStatusResult getPaymentStatus(String providerReference) {
        if (!configured() || !isValidProviderReference(providerReference)) {
            return PaymentProviderStatusResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
        }

        try {
            String normalizedReference = providerReference.strip();
            String encodedReference = UriUtils.encodePathSegment(normalizedReference, StandardCharsets.UTF_8);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint("/v1/checkout/sessions/" + encodedReference),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(null)),
                    new ParameterizedTypeReference<>() {
                    });
            Map<String, Object> responseBody = response.getBody();
            String sessionId = stringValue(responseBody, "id");
            String rawSessionStatus = stringValue(responseBody, "status");
            String rawPaymentStatus = stringValue(responseBody, "payment_status");
            if (rawSessionStatus == null && rawPaymentStatus == null) {
                log.warn("Stripe payment status retrieval returned an incomplete provider response.");
                return PaymentProviderStatusResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
            }

            String sessionStatus = normalizeStatus(rawSessionStatus);
            String paymentStatus = normalizeStatus(rawPaymentStatus);
            return new PaymentProviderStatusResult(
                    true,
                    PROVIDER_NAME,
                    sessionId == null ? normalizedReference : sessionId,
                    mapPaymentStatus(sessionStatus, paymentStatus),
                    sessionStatus + ":" + paymentStatus,
                    "Stripe payment status retrieved successfully.");
        } catch (RuntimeException exception) {
            logProviderFailure("Stripe payment status retrieval failed", exception);
            return PaymentProviderStatusResult.unavailable(PROVIDER_NAME, PAYMENT_UNAVAILABLE_MESSAGE);
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
        if (path == null || !path.startsWith("/") || path.contains("..")) {
            throw new IllegalArgumentException("Stripe API path is invalid.");
        }

        URI baseUri;
        try {
            baseUri = new URI(paymentProperties.stripeApiBaseUrl());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Stripe API base URL is malformed.", exception);
        }

        String scheme = normalizeScheme(baseUri);
        String host = normalizeHost(baseUri.getHost());
        if (host.isBlank()
                || baseUri.getUserInfo() != null
                || baseUri.getRawQuery() != null
                || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Stripe API base URL must be an absolute HTTP(S) origin or base path.");
        }
        if (!"https".equals(scheme) && !("http".equals(scheme) && isLoopbackHost(host))) {
            throw new IllegalArgumentException("Stripe API base URL must use HTTPS unless it targets localhost.");
        }

        try {
            return new URI(baseUri.getScheme(), null, baseUri.getHost(), baseUri.getPort(),
                    joinPaths(baseUri.getRawPath(), path), null, null)
                    .toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Stripe API endpoint could not be built.", exception);
        }
    }

    private static String joinPaths(String basePath, String requestPath) {
        if (basePath == null || basePath.isBlank() || "/".equals(basePath)) {
            return requestPath;
        }
        String normalizedBasePath = basePath.endsWith("/")
                ? basePath.substring(0, basePath.length() - 1)
                : basePath;
        return normalizedBasePath + requestPath;
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = normalizeHost(host);
        return "localhost".equals(normalized)
                || normalized.startsWith("127.")
                || "::1".equals(normalized);
    }

    private static String normalizeScheme(URI uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String normalized = host.strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2
                ? normalized.substring(1, normalized.length() - 1)
                : normalized;
    }

    private static void addMetadata(MultiValueMap<String, String> body, CommerceCheckout checkout) {
        if (checkout.order() != null) {
            if (checkout.order().getId() != null) {
                addMetadataValue(body, "metadata[order_id]", String.valueOf(checkout.order().getId()));
            }
            if (checkout.order().getOrderNumber() != null && !checkout.order().getOrderNumber().isBlank()) {
                addMetadataValue(body, "metadata[order_number]", checkout.order().getOrderNumber());
            }
        }
        if (checkout.paymentRecord() != null && checkout.paymentRecord().getId() != null) {
            addMetadataValue(body, "metadata[payment_id]", String.valueOf(checkout.paymentRecord().getId()));
        }
        if (checkout.student() != null && checkout.student().getId() != null) {
            addMetadataValue(body, "metadata[student_id]", String.valueOf(checkout.student().getId()));
        }
    }

    private static void addLineItems(MultiValueMap<String, String> body, CommerceCheckout checkout) {
        List<OrderItem> items = checkout.orderItems() == null ? List.of() : checkout.orderItems();
        if (!hasProviderSafeLineItems(items, checkout.currencyCode())) {
            addFallbackLineItem(body, checkout);
            return;
        }

        for (int index = 0; index < items.size(); index++) {
            OrderItem item = items.get(index);
            body.add("line_items[" + index + "][price_data][currency]", currency(checkout.currencyCode()));
            body.add("line_items[" + index + "][price_data][product_data][name]", productName(item));
            body.add("line_items[" + index + "][price_data][unit_amount]",
                    String.valueOf(toMinorUnits(item.getUnitPrice(), checkout.currencyCode())));
            body.add("line_items[" + index + "][quantity]", String.valueOf(item.getQuantity()));
        }
    }

    private static void addFallbackLineItem(MultiValueMap<String, String> body, CommerceCheckout checkout) {
        body.add("line_items[0][price_data][currency]", currency(checkout.currencyCode()));
        body.add("line_items[0][price_data][product_data][name]", DEFAULT_FALLBACK_PRODUCT_NAME);
        body.add("line_items[0][price_data][unit_amount]",
                String.valueOf(toMinorUnits(checkout.totalAmount(), checkout.currencyCode())));
        body.add("line_items[0][quantity]", "1");
    }

    private static boolean hasProviderSafeLineItems(List<OrderItem> items, String currencyCode) {
        if (items == null || items.isEmpty() || items.size() > MAX_CHECKOUT_LINE_ITEMS) {
            return false;
        }
        for (OrderItem item : items) {
            if (item == null || item.getQuantity() == null || item.getQuantity() < 1) {
                return false;
            }
            try {
                if (toMinorUnits(item.getUnitPrice(), currencyCode) <= 0) {
                    return false;
                }
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return true;
    }

    private static String currency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        String normalized = currencyCode.strip();
        if (!CURRENCY_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Currency code must be a three-letter ISO 4217 code.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String productName(OrderItem item) {
        String title = item == null ? null : item.getItemTitleSnapshot();
        if (title == null || title.isBlank()) {
            return item == null || item.getItemType() == null || item.getItemType().isBlank()
                    ? DEFAULT_PRODUCT_NAME
                    : safeProviderText(item.getItemType() + " " + item.getItemId(),
                            DEFAULT_PRODUCT_NAME,
                            MAX_PRODUCT_NAME_LENGTH);
        }
        return safeProviderText(title, DEFAULT_PRODUCT_NAME, MAX_PRODUCT_NAME_LENGTH);
    }

    private static long toMinorUnits(BigDecimal amount, String currencyCode) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        String normalizedCurrency = currencyCode == null ? "" : currencyCode.strip().toUpperCase(Locale.ROOT);
        BigDecimal multiplier = ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)
                ? BigDecimal.ONE
                : MINOR_UNIT_MULTIPLIER;
        try {
            long minorUnits = safeAmount.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
            if (minorUnits <= 0) {
                throw new IllegalArgumentException("Stripe amount must be greater than zero.");
            }
            return minorUnits;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Stripe amount is outside the supported range.", exception);
        }
    }

    private static PaymentStatus mapPaymentStatus(String sessionStatus, String paymentStatus) {
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

    private static String normalizeStatus(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String stringValue(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private static String requireRedirectUrl(String value, String label) {
        if (value == null || value.isBlank() || value.length() > MAX_REDIRECT_URL_LENGTH) {
            throw new IllegalArgumentException(label + " must be a configured absolute HTTP(S) URL.");
        }
        URI uri;
        try {
            uri = new URI(value.strip());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(label + " must be a valid URL.", exception);
        }
        String scheme = normalizeScheme(uri);
        if (!("http".equals(scheme) || "https".equals(scheme))
                || normalizeHost(uri.getHost()).isBlank()
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(label + " must be an absolute HTTP(S) URL without embedded credentials.");
        }
        return uri.toString();
    }

    private static boolean isValidProviderReference(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return false;
        }
        String normalized = providerReference.strip();
        return normalized.length() <= MAX_PROVIDER_REFERENCE_LENGTH
                && PROVIDER_REFERENCE_PATTERN.matcher(normalized).matches();
    }

    private static void addMetadataValue(MultiValueMap<String, String> body, String key, String value) {
        String safeValue = safeProviderText(value, "", MAX_METADATA_VALUE_LENGTH);
        if (!safeValue.isBlank()) {
            body.add(key, safeValue);
        }
    }

    private static String safeProviderText(String value, String fallback, int maxLength) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean previousWasWhitespace = false;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character)) {
                if (!previousWasWhitespace) {
                    builder.append(' ');
                    previousWasWhitespace = true;
                }
            } else {
                builder.append(character);
                previousWasWhitespace = false;
            }
            if (builder.length() >= maxLength) {
                break;
            }
        }
        String safe = builder.toString().strip();
        return safe.isBlank() ? fallback : safe;
    }

    private static void logProviderFailure(String message, RuntimeException exception) {
        String exceptionType = exception == null ? "unknown" : exception.getClass().getSimpleName();
        log.warn("{}: {}", message, exceptionType);
        log.debug(message, exception);
    }
}
