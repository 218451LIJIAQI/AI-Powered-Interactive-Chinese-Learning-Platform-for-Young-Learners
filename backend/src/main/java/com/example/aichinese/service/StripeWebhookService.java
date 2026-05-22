package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

    private final PaymentProperties paymentProperties;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public StripeWebhookService(
            PaymentProperties paymentProperties,
            PaymentService paymentService,
            ObjectMapper objectMapper) {
        this.paymentProperties = paymentProperties;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    public StripeWebhookResultResponse handleWebhook(String payload, String signatureHeader) {
        requireValidSignature(payload, signatureHeader);

        JsonNode root = parsePayload(payload);
        String eventId = text(root, "id");
        String eventType = text(root, "type");
        JsonNode objectNode = root.path("data").path("object");
        String sessionId = text(objectNode, "id");
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook payload is missing a checkout session id.");
        }

        PaymentStatus paymentStatus = mapPaymentStatus(eventType, text(objectNode, "status"), text(objectNode, "payment_status"));
        String providerStatus = statusSummary(eventType, text(objectNode, "status"), text(objectNode, "payment_status"));
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
        return new StripeWebhookResultResponse(
                eventId,
                eventType,
                result.paymentId(),
                result.paymentStatus(),
                result.providerStatus(),
                result.processed());
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
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook signature timestamp is outside the allowed tolerance.");
        }

        String signedPayload = timestampText + "." + payload;
        String expectedSignature = hmacSha256Hex(secret, signedPayload);
        boolean signatureMatches = actualSignatures.stream()
                .anyMatch(actualSignature -> constantTimeEquals(expectedSignature, actualSignature));
        if (!signatureMatches) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook signature is invalid.");
        }
    }

    private SignatureHeaderParts parseSignatureHeader(String signatureHeader) {
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

    private String hmacSha256Hex(String secret, String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Stripe webhook signature could not be verified.");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < expectedBytes.length; index++) {
            result |= expectedBytes[index] ^ actualBytes[index];
        }
        return result == 0;
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe webhook payload is invalid.");
        }
    }

    private PaymentStatus mapPaymentStatus(String eventType, String sessionStatus, String paymentStatus) {
        String normalizedEvent = normalize(eventType);
        String normalizedSession = normalize(sessionStatus);
        String normalizedPayment = normalize(paymentStatus);
        if ("CHECKOUT.SESSION.COMPLETED".equals(normalizedEvent)
                || "PAID".equals(normalizedPayment)
                || "NO_PAYMENT_REQUIRED".equals(normalizedPayment)) {
            return PaymentStatus.PAID;
        }
        if ("CHECKOUT.SESSION.EXPIRED".equals(normalizedEvent) || "EXPIRED".equals(normalizedSession)) {
            return PaymentStatus.CANCELLED;
        }
        if ("COMPLETE".equals(normalizedSession) && "UNPAID".equals(normalizedPayment)) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
    }

    private String statusSummary(String eventType, String sessionStatus, String paymentStatus) {
        return normalize(eventType) + ":" + normalize(sessionStatus) + ":" + normalize(paymentStatus);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase().replace('-', '_');
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private record SignatureHeaderParts(String timestamp, List<String> v1Signatures) {
    }
}
