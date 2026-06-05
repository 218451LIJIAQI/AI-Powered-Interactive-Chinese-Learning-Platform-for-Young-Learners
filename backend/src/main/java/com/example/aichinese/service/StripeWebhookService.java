package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.dto.commerce.StripeWebhookResultResponse;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;
    public static final int MAX_WEBHOOK_PAYLOAD_BYTES = 256 * 1024;
    private static final int MAX_SIGNATURE_HEADER_LENGTH = 4096;
    private static final int MAX_PROVIDER_STATUS_LENGTH = 100;
    private static final Set<String> SUPPORTED_CHECKOUT_SESSION_EVENTS = Set.of(
            "CHECKOUT.SESSION.COMPLETED",
            "CHECKOUT.SESSION.ASYNC_PAYMENT_SUCCEEDED",
            "CHECKOUT.SESSION.ASYNC_PAYMENT_FAILED",
            "CHECKOUT.SESSION.EXPIRED");

    private final PaymentProperties paymentProperties;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public StripeWebhookService(
            PaymentProperties paymentProperties,
            PaymentService paymentService,
            ObjectMapper objectMapper) {
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public StripeWebhookResultResponse handleWebhook(String payload, String signatureHeader) {
        requireReasonableWebhookInput(payload, signatureHeader);
        requireValidSignature(payload, signatureHeader);

        JsonNode root = parsePayload(payload);
        String eventId = text(root, "id");
        String eventType = text(root, "type");
        JsonNode objectNode = root.path("data").path("object");
        String stripeSessionStatus = text(objectNode, "status");
        String stripePaymentStatus = text(objectNode, "payment_status");
        if (!isSupportedCheckoutSessionEvent(eventType)) {
            log.debug("Ignoring unsupported Stripe event: type={}, id={}", eventType, eventId);
            return new StripeWebhookResultResponse(
                    eventId,
                    eventType,
                    null,
                    PaymentStatus.PENDING,
                    statusSummary(eventType, stripeSessionStatus, stripePaymentStatus),
                    false);
        }
        if (!"checkout.session".equals(text(objectNode, "object"))) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook payload object is not a checkout session.");
        }
        String sessionId = text(objectNode, "id");
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook payload is missing a checkout session id.");
        }

        PaymentStatus paymentStatus = mapPaymentStatus(eventType, stripeSessionStatus, stripePaymentStatus);
        String providerStatus = statusSummary(eventType, stripeSessionStatus, stripePaymentStatus);
        Optional<StripeWebhookResultResponse> processed = paymentService.applyProviderPaymentStatus(
                "stripe",
                sessionId,
                new PaymentProviderStatusResult(
                        true,
                        "stripe",
                        sessionId,
                        paymentStatus,
                        providerStatus,
                        "Stripe webhook payment status received."));

        StripeWebhookResultResponse result = processed.orElseGet(() -> new StripeWebhookResultResponse(
                eventId,
                eventType,
                null,
                paymentStatus,
                providerStatus,
                false));
        StripeWebhookResultResponse finalResult = new StripeWebhookResultResponse(
                eventId,
                eventType,
                result.paymentId(),
                result.paymentStatus(),
                result.providerStatus(),
                result.processed());
        log.info("Stripe webhook processed: eventId={}, type={}, paymentId={}, status={}, applied={}",
                eventId, eventType, finalResult.paymentId(), finalResult.paymentStatus(), finalResult.processed());
        return finalResult;
    }

    private static void requireReasonableWebhookInput(String payload, String signatureHeader) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > MAX_WEBHOOK_PAYLOAD_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook payload is invalid.");
        }
        if (signatureHeader != null && signatureHeader.length() > MAX_SIGNATURE_HEADER_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook signature is invalid.");
        }
    }

    private void requireValidSignature(String payload, String signatureHeader) {
        String secret = paymentProperties.stripeWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Stripe webhook handling is not configured.");
        }
        SignatureHeaderParts signatureParts = parseSignatureHeader(signatureHeader);
        String timestampText = signatureParts.timestamp();
        List<String> actualSignatures = signatureParts.v1Signatures();
        if (timestampText == null || actualSignatures.isEmpty() || payload == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook signature is invalid.");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook signature is invalid.");
        }
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > SIGNATURE_TOLERANCE_SECONDS) {
            log.warn("Stripe webhook rejected: signature timestamp outside tolerance (timestamp={})", timestamp);
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook signature timestamp is outside the allowed tolerance.");
        }

        String signedPayload = timestampText + "." + payload;
        String expectedSignature = hmacSha256Hex(secret, signedPayload);
        boolean signatureMatches = actualSignatures.stream()
                .anyMatch(actualSignature -> constantTimeEquals(expectedSignature, actualSignature));
        if (!signatureMatches) {
            log.warn("Stripe webhook rejected: HMAC signature mismatch");
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook signature is invalid.");
        }
    }

    private static SignatureHeaderParts parseSignatureHeader(String signatureHeader) {
        Map<String, List<String>> parts = new LinkedHashMap<>();
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return new SignatureHeaderParts(null, List.of());
        }
        Arrays.stream(signatureHeader.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .forEach(part -> {
                    int separator = part.indexOf('=');
                    if (separator > 0 && separator < part.length() - 1) {
                        parts.computeIfAbsent(part.substring(0, separator), key -> new ArrayList<>())
                                .add(part.substring(separator + 1));
                    }
                });
        String timestamp = parts.getOrDefault("t", List.of()).stream().findFirst().orElse(null);
        return new SignatureHeaderParts(timestamp, List.copyOf(parts.getOrDefault("v1", List.of())));
    }

    private static String hmacSha256Hex(String secret, String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Stripe webhook signature could not be verified.");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook payload is invalid.");
        }
    }

    private static PaymentStatus mapPaymentStatus(String eventType, String sessionStatus, String paymentStatus) {
        String normalizedEvent = normalize(eventType);
        String normalizedSession = normalize(sessionStatus);
        String normalizedPayment = normalize(paymentStatus);
        if ("PAID".equals(normalizedPayment)
                || "NO_PAYMENT_REQUIRED".equals(normalizedPayment)
                || "CHECKOUT.SESSION.ASYNC_PAYMENT_SUCCEEDED".equals(normalizedEvent)) {
            return PaymentStatus.PAID;
        }
        if ("CHECKOUT.SESSION.ASYNC_PAYMENT_FAILED".equals(normalizedEvent)) {
            return PaymentStatus.FAILED;
        }
        if ("CHECKOUT.SESSION.EXPIRED".equals(normalizedEvent) || "EXPIRED".equals(normalizedSession)) {
            return PaymentStatus.CANCELLED;
        }
        if ("COMPLETE".equals(normalizedSession) && "UNPAID".equals(normalizedPayment)) {
            return PaymentStatus.PENDING;
        }
        return PaymentStatus.PENDING;
    }

    private static boolean isSupportedCheckoutSessionEvent(String eventType) {
        return SUPPORTED_CHECKOUT_SESSION_EVENTS.contains(normalize(eventType));
    }

    private static String statusSummary(String eventType, String sessionStatus, String paymentStatus) {
        String summary = normalize(eventType) + ":" + normalize(sessionStatus) + ":" + normalize(paymentStatus);
        return summary.length() <= MAX_PROVIDER_STATUS_LENGTH ? summary : summary.substring(0, MAX_PROVIDER_STATUS_LENGTH);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private record SignatureHeaderParts(String timestamp, List<String> v1Signatures) {
    }
}
