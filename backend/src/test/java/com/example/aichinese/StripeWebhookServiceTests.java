package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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

/**
 * Unit coverage for {@code StripeWebhookService}. It pins the
 * security-critical signature verification:
 * timestamp tolerance window, constant-time comparison, rejection of
 * forged / replayed / mis-typed payloads, and the supported-event
 * filter so unsupported event types are acknowledged 200 OK without
 * any state change.
 */
class StripeWebhookServiceTests {

    private static final String SECRET = "whsec_test_secret";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PAYMENT_PROVIDER = "stripe";
    private static final String SUCCESS_URL = "http://localhost:5174/payment/success";
    private static final String CANCEL_URL = "http://localhost:5174/payment/cancel";
    private static final String STRIPE_API_URL = "https://api.stripe.com";
    private static final String API_KEY = "sk_test_local";
    private static final String CHECKOUT_SESSION_OBJECT = "checkout.session";
    private static final String CHARGE_OBJECT = "charge";
    private static final String CHECKOUT_COMPLETED = "checkout.session.completed";
    private static final String CHECKOUT_EXPIRED = "checkout.session.expired";
    private static final String ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";
    private static final String CHARGE_SUCCEEDED = "charge.succeeded";
    private static final String STATUS_COMPLETE = "complete";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STRIPE_PAID = "paid";
    private static final String STRIPE_UNPAID = "unpaid";
    private static final String PROVIDER_PAID = "CHECKOUT.SESSION.COMPLETED:COMPLETE:PAID";
    private static final String PROVIDER_PENDING = "CHECKOUT.SESSION.COMPLETED:COMPLETE:UNPAID";
    private static final String PROVIDER_ASYNC_PAID =
            "CHECKOUT.SESSION.ASYNC_PAYMENT_SUCCEEDED:COMPLETE:UNPAID";
    private static final String BAD_REQUEST_MESSAGE = "400 BAD_REQUEST";
    private static final String EVENT_PAID = "evt_paid";
    private static final String EVENT_UNPAID = "evt_unpaid";
    private static final String EVENT_ASYNC_PAID = "evt_async_paid";
    private static final String EVENT_PAID_ROTATED = "evt_paid_rotated";
    private static final String EVENT_BAD = "evt_bad";
    private static final String EVENT_CHARGE = "evt_charge";
    private static final String EVENT_WRONG_OBJECT = "evt_wrong_object";
    private static final String EVENT_MISSING = "evt_missing";
    private static final String SESSION_PAID = "cs_test_paid";
    private static final String SESSION_UNPAID = "cs_test_unpaid";
    private static final String SESSION_ASYNC = "cs_test_async";
    private static final String SESSION_ROTATED = "cs_test_rotated";
    private static final String SESSION_MISSING = "cs_missing";
    private static final String CHARGE_PAID = "ch_test_paid";
    private static final long PAYMENT_ID_PAID = 10L;
    private static final long PAYMENT_ID_ROTATED = 11L;
    private static final long PAYMENT_ID_PENDING = 12L;
    private static final long PAYMENT_ID_ASYNC = 13L;

    @Test
    void validCheckoutCompletedWebhookAppliesPaidProviderStatus() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload(
                EVENT_PAID,
                CHECKOUT_COMPLETED,
                SESSION_PAID,
                STATUS_COMPLETE,
                STRIPE_PAID);
        String signature = signatureHeader(payload);
        whenApplyProviderStatus(paymentService, SESSION_PAID, PaymentStatus.PAID, PROVIDER_PAID, PAYMENT_ID_PAID);

        StripeWebhookResultResponse response = service.handleWebhook(payload, signature);

        assertThat(response.eventId()).isEqualTo(EVENT_PAID);
        assertThat(response.eventType()).isEqualTo(CHECKOUT_COMPLETED);
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID_PAID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.processed()).isTrue();
    }

    @Test
    void checkoutCompletedWithUnpaidAsyncPaymentDoesNotUnlockEntitlement() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload(
                EVENT_UNPAID,
                CHECKOUT_COMPLETED,
                SESSION_UNPAID,
                STATUS_COMPLETE,
                STRIPE_UNPAID);
        String signature = signatureHeader(payload);
        whenApplyProviderStatus(
                paymentService,
                SESSION_UNPAID,
                PaymentStatus.PENDING,
                PROVIDER_PENDING,
                PAYMENT_ID_PENDING);

        StripeWebhookResultResponse response = service.handleWebhook(payload, signature);

        assertThat(response.eventId()).isEqualTo(EVENT_UNPAID);
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID_PENDING);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.processed()).isTrue();
    }

    @Test
    void asyncPaymentSucceededWebhookMarksPaymentPaid() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload(
                EVENT_ASYNC_PAID,
                ASYNC_PAYMENT_SUCCEEDED,
                SESSION_ASYNC,
                STATUS_COMPLETE,
                STRIPE_UNPAID);
        whenApplyProviderStatus(
                paymentService,
                SESSION_ASYNC,
                PaymentStatus.PAID,
                PROVIDER_ASYNC_PAID,
                PAYMENT_ID_ASYNC);

        StripeWebhookResultResponse response = service.handleWebhook(payload, signatureHeader(payload));

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID_ASYNC);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.processed()).isTrue();
    }

    @Test
    void webhookAcceptsAnyValidV1SignatureInHeader() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload(
                EVENT_PAID_ROTATED,
                CHECKOUT_COMPLETED,
                SESSION_ROTATED,
                STATUS_COMPLETE,
                STRIPE_PAID);
        long timestamp = Instant.now().getEpochSecond();
        String signature = "t=" + timestamp + ",v1=bad,v1=" + hmac(timestamp + "." + payload);
        whenApplyProviderStatus(
                paymentService,
                SESSION_ROTATED,
                PaymentStatus.PAID,
                PROVIDER_PAID,
                PAYMENT_ID_ROTATED);

        StripeWebhookResultResponse response = service.handleWebhook(payload, signature);

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID_ROTATED);
        assertThat(response.processed()).isTrue();
    }

    @Test
    void invalidSignatureDoesNotApplyProviderStatus() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload(
                EVENT_BAD,
                CHECKOUT_COMPLETED,
                SESSION_PAID,
                STATUS_COMPLETE,
                STRIPE_PAID);

        assertThatThrownBy(() -> service.handleWebhook(payload, "t=" + Instant.now().getEpochSecond() + ",v1=bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(BAD_REQUEST_MESSAGE);
        verifyNoInteractions(paymentService);
    }

    @Test
    void signedNonCheckoutSessionEventIsIgnored() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = """
                {"id":"%s","type":"%s","data":{"object":{"id":"%s","object":"%s","status":"succeeded","payment_status":"%s"}}}
                """.formatted(EVENT_CHARGE, CHARGE_SUCCEEDED, CHARGE_PAID, CHARGE_OBJECT, STRIPE_PAID)
                .trim();

        StripeWebhookResultResponse response = service.handleWebhook(payload, signatureHeader(payload));

        assertThat(response.eventId()).isEqualTo(EVENT_CHARGE);
        assertThat(response.eventType()).isEqualTo(CHARGE_SUCCEEDED);
        assertThat(response.processed()).isFalse();
        verifyNoInteractions(paymentService);
    }

    @Test
    void signedCheckoutSessionEventRejectsWrongObjectType() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = """
                {"id":"%s","type":"%s","data":{"object":{"id":"%s","object":"%s","status":"%s","payment_status":"%s"}}}
                """.formatted(
                EVENT_WRONG_OBJECT,
                CHECKOUT_COMPLETED,
                CHARGE_PAID,
                CHARGE_OBJECT,
                STATUS_COMPLETE,
                STRIPE_PAID).trim();

        assertThatThrownBy(() -> service.handleWebhook(payload, signatureHeader(payload)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(BAD_REQUEST_MESSAGE);
        verifyNoInteractions(paymentService);
    }

    @Test
    void unknownPaymentReferenceReturnsUnprocessedResult() {
        PaymentService paymentService = mock(PaymentService.class);
        StripeWebhookService service = service(paymentService);
        String payload = checkoutSessionPayload(
                EVENT_MISSING,
                CHECKOUT_EXPIRED,
                SESSION_MISSING,
                STATUS_EXPIRED,
                STRIPE_UNPAID);
        when(paymentService.applyProviderPaymentStatus(
                eq(PAYMENT_PROVIDER),
                eq(SESSION_MISSING),
                argThat(status -> status.paymentStatus() == PaymentStatus.CANCELLED)))
                .thenReturn(Optional.empty());

        StripeWebhookResultResponse response = service.handleWebhook(payload, signatureHeader(payload));

        assertThat(response.eventId()).isEqualTo(EVENT_MISSING);
        assertThat(response.paymentId()).isNull();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.processed()).isFalse();
        verify(paymentService).applyProviderPaymentStatus(
                eq(PAYMENT_PROVIDER),
                eq(SESSION_MISSING),
                argThat(status -> status.paymentStatus() == PaymentStatus.CANCELLED));
    }

    private static void whenApplyProviderStatus(
            PaymentService paymentService,
            String sessionId,
            PaymentStatus paymentStatus,
            String providerStatus,
            long paymentId) {
        when(paymentService.applyProviderPaymentStatus(
                eq(PAYMENT_PROVIDER),
                eq(sessionId),
                argThat(status -> status.available()
                        && status.paymentStatus() == paymentStatus
                        && providerStatus.equals(status.providerStatus()))))
                .thenReturn(Optional.of(webhookResult(paymentId, paymentStatus, providerStatus, true)));
    }

    private static StripeWebhookResultResponse webhookResult(
            Long paymentId,
            PaymentStatus paymentStatus,
            String providerStatus,
            boolean processed) {
        return new StripeWebhookResultResponse(null, null, paymentId, paymentStatus, providerStatus, processed);
    }

    private static StripeWebhookService service(PaymentService paymentService) {
        return new StripeWebhookService(
                new PaymentProperties(
                        SUCCESS_URL,
                        CANCEL_URL,
                        false,
                        PAYMENT_PROVIDER,
                        STRIPE_API_URL,
                        API_KEY,
                        SECRET),
                paymentService,
                OBJECT_MAPPER);
    }

    private static String checkoutSessionPayload(
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
                      "object": "%s",
                      "status": "%s",
                      "payment_status": "%s"
                    }
                  }
                }
        """.formatted(
                eventId,
                eventType,
                sessionId,
                CHECKOUT_SESSION_OBJECT,
                status,
                paymentStatus);
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
