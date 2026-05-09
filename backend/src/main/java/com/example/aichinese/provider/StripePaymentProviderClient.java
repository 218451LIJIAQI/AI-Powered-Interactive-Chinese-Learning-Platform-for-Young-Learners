package com.example.aichinese.provider;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.service.model.CommerceCheckout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class StripePaymentProviderClient implements PaymentProviderClient {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProviderClient.class);
    private static final String STRIPE_API_BASE_URL = "https://api.stripe.com";

    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public StripePaymentProviderClient(PaymentProperties paymentProperties, ObjectMapper objectMapper) {
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(paymentProperties.timeoutSeconds()))
                .build();
    }

    @Override
    public String providerName() {
        return "stripe";
    }

    @Override
    public PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout) {
        if (!paymentProperties.isAvailableForRealCheckout()) {
            return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STRIPE_API_BASE_URL + "/v1/checkout/sessions"))
                    .timeout(Duration.ofSeconds(paymentProperties.timeoutSeconds()))
                    .header("Authorization", "Bearer " + paymentProperties.stripeSecretKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(buildCheckoutPayload(checkout)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Stripe checkout session creation failed with status {}", response.statusCode());
                return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
            }

            JsonNode body = objectMapper.readTree(response.body());
            String reference = body.path("id").asText(null);
            String checkoutUrl = body.path("url").asText(null);
            return new PaymentProviderCheckoutResult(
                    true,
                    providerName(),
                    reference,
                    checkoutUrl,
                    body.path("status").asText("PENDING_PROVIDER_CONFIRMATION"),
                    "Stripe test checkout session created.");
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Stripe checkout session creation failed safely: {}", exception.getMessage());
            return PaymentProviderCheckoutResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public PaymentProviderStatusResult getPaymentStatus(String providerReference) {
        if (!paymentProperties.isAvailableForRealCheckout()) {
            return PaymentProviderStatusResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STRIPE_API_BASE_URL + "/v1/checkout/sessions/" + providerReference))
                    .timeout(Duration.ofSeconds(paymentProperties.timeoutSeconds()))
                    .header("Authorization", "Bearer " + paymentProperties.stripeSecretKey())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Stripe payment status verification failed with status {}", response.statusCode());
                return PaymentProviderStatusResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
            }

            JsonNode body = objectMapper.readTree(response.body());
            String paymentStatusValue = body.path("payment_status").asText("unpaid");
            String sessionStatus = body.path("status").asText("open");
            return new PaymentProviderStatusResult(
                    true,
                    providerName(),
                    providerReference,
                    mapStripePaymentStatus(paymentStatusValue, sessionStatus),
                    sessionStatus,
                    "Stripe checkout session retrieved successfully.");
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Stripe payment status verification failed safely: {}", exception.getMessage());
            return PaymentProviderStatusResult.unavailable(providerName(), PAYMENT_UNAVAILABLE_MESSAGE);
        }
    }

    private String buildCheckoutPayload(CommerceCheckout checkout) {
        List<String> params = new ArrayList<>();
        params.add(form("mode", "payment"));
        params.add(form("success_url", checkout.successUrl()));
        params.add(form("cancel_url", checkout.cancelUrl()));
        params.add(form("client_reference_id", String.valueOf(checkout.paymentRecord().getId())));
        params.add(form("metadata[local_payment_id]", String.valueOf(checkout.paymentRecord().getId())));
        params.add(form("metadata[local_order_id]", String.valueOf(checkout.order().getId())));
        params.add(form("metadata[local_order_number]", checkout.order().getOrderNumber()));

        for (int i = 0; i < checkout.orderItems().size(); i++) {
            var item = checkout.orderItems().get(i);
            params.add(form("line_items[" + i + "][quantity]", String.valueOf(item.getQuantity())));
            params.add(form("line_items[" + i + "][price_data][currency]", checkout.currencyCode().toLowerCase(java.util.Locale.ROOT)));
            params.add(form("line_items[" + i + "][price_data][product_data][name]", item.getItemTitleSnapshot()));
            params.add(form("line_items[" + i + "][price_data][unit_amount]", String.valueOf(toStripeAmount(item.getUnitPrice()))));
        }

        return String.join("&", params);
    }

    private long toStripeAmount(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private PaymentStatus mapStripePaymentStatus(String paymentStatus, String sessionStatus) {
        if ("paid".equalsIgnoreCase(paymentStatus) || "no_payment_required".equalsIgnoreCase(paymentStatus)) {
            return PaymentStatus.PAID;
        }

        if ("expired".equalsIgnoreCase(sessionStatus)) {
            return PaymentStatus.CANCELLED;
        }

        if ("open".equalsIgnoreCase(sessionStatus)) {
            return PaymentStatus.PENDING;
        }

        return PaymentStatus.FAILED;
    }
}
