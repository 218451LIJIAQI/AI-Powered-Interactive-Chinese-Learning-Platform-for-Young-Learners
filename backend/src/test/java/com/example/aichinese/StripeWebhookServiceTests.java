package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.StripeWebhookResultResponse;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.StripeWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;

class StripeWebhookServiceTests {

    private static final String SECRET = "whsec_test_secret";

    @Test
    void validCheckoutCompletedWebhookAppliesPaidProviderStatus() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload("evt_paid", "checkout.session.completed", "cs_test_paid", "complete", "paid");
        String signature = signatureHeader(payload);
        when(paymentService.applyProviderPaymentStatus(
                eq("stripe"),
                eq("cs_test_paid"),
                argThat(status -> status.available()
                        && status.paymentStatus() == PaymentStatus.PAID
                        && "CHECKOUT.SESSION.COMPLETED:COMPLETE:PAID".equals(status.providerStatus()))))
                .thenReturn(Optional.of(new StripeWebhookResultResponse(
                        null,
                        null,
                        10L,
                        PaymentStatus.PAID,
                        "CHECKOUT.SESSION.COMPLETED:COMPLETE:PAID",
                        true)));

        StripeWebhookResultResponse response = service.handleWebhook(payload, signature);

        assertThat(response.eventId()).isEqualTo("evt_paid");
        assertThat(response.eventType()).isEqualTo("checkout.session.completed");
        assertThat(response.paymentId()).isEqualTo(10L);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.processed()).isTrue();
    }

    @Test
    void webhookAcceptsAnyValidV1SignatureInHeader() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload("evt_paid_rotated", "checkout.session.completed", "cs_test_rotated", "complete", "paid");
        long timestamp = Instant.now().getEpochSecond();
        String signature = "t=" + timestamp + ",v1=bad,v1=" + hmac(timestamp + "." + payload);
        when(paymentService.applyProviderPaymentStatus(
                eq("stripe"),
                eq("cs_test_rotated"),
                argThat(status -> status.available() && status.paymentStatus() == PaymentStatus.PAID)))
                .thenReturn(Optional.of(new StripeWebhookResultResponse(
                        null,
                        null,
                        11L,
                        PaymentStatus.PAID,
                        "CHECKOUT.SESSION.COMPLETED:COMPLETE:PAID",
                        true)));

        StripeWebhookResultResponse response = service.handleWebhook(payload, signature);

        assertThat(response.paymentId()).isEqualTo(11L);
        assertThat(response.processed()).isTrue();
    }

    @Test
    void invalidSignatureDoesNotApplyProviderStatus() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload("evt_bad", "checkout.session.completed", "cs_test_paid", "complete", "paid");

        assertThatThrownBy(() -> service.handleWebhook(payload, "t=" + Instant.now().getEpochSecond() + ",v1=bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(paymentService);
    }

    @Test
    void unknownPaymentReferenceReturnsUnprocessedResult() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload("evt_missing", "checkout.session.expired", "cs_missing", "expired", "unpaid");
        when(paymentService.applyProviderPaymentStatus(
                eq("stripe"),
                eq("cs_missing"),
                argThat(status -> status.paymentStatus() == PaymentStatus.CANCELLED)))
                .thenReturn(Optional.empty());

        StripeWebhookResultResponse response = service.handleWebhook(payload, signatureHeader(payload));

        assertThat(response.eventId()).isEqualTo("evt_missing");
        assertThat(response.paymentId()).isNull();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.processed()).isFalse();
        verify(paymentService).applyProviderPaymentStatus(
                eq("stripe"),
                eq("cs_missing"),
                argThat(status -> status.paymentStatus() == PaymentStatus.CANCELLED));
    }

    private StripeWebhookService service(PaymentService paymentService) {
        return new StripeWebhookService(
                new PaymentProperties(
                        "http://localhost:5173/payment/success",
                        "http://localhost:5173/payment/cancel",
                        false,
                        "stripe",
                        "https://api.stripe.com",
                        "sk_test_local",
                        SECRET),
                paymentService,
                new ObjectMapper());
    }

    private String checkoutSessionPayload(
            String eventId,
            String eventType,
            String sessionId,
            String status,
            String paymentStatus) {
        return """
                {
                  "id": "%s",
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "status": "%s",
                      "payment_status": "%s"
                    }
                  }
                }
                """.formatted(eventId, eventType, sessionId, status, paymentStatus);
    }

    private String signatureHeader(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        return "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload);
    }

    private String hmac(String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
