package com.example.aichinese.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.commerce.StudentPaymentRecordResponse;
import com.example.aichinese.dto.commerce.StripeWebhookResultResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.StripeWebhookService;

import jakarta.validation.constraints.Size;

/**
 * Public-facing payment-status read endpoint + the Stripe webhook
 * settlement entry point.
 *
 * <h2>Status read</h2>
 * <p>{@code GET /status/{paymentId}} is row-locked to the calling
 * student inside {@link PaymentService#verifyPaymentStatus} — direct-
 * object reference attacks on another learner's payment row fail
 * with 404, not a leak.</p>
 *
 * <h2>Stripe webhook</h2>
 * <p>{@code POST /webhooks/stripe} accepts the raw payload + the
 * {@code Stripe-Signature} header and delegates to
 * {@link StripeWebhookService}, which performs HMAC-SHA-256 signature
 * verification (with 5-minute replay tolerance and constant-time
 * comparison) before any state change. Configuration problems raise
 * 503 (Stripe retries); signature / payload problems raise 400
 * (Stripe stops retrying). The endpoint is intentionally unauthenticated
 * because the signature itself is the authentication.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final StripeWebhookService stripeWebhookService;

    public PaymentController(PaymentService paymentService, StripeWebhookService stripeWebhookService) {
        this.paymentService = paymentService;
        this.stripeWebhookService = stripeWebhookService;
    }

    @GetMapping("/status/{paymentId}")
    public ApiResponse<StudentPaymentRecordResponse> getPaymentStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long paymentId) {
        return ApiResponse.success(
                "Payment status retrieved successfully.",
                paymentService.verifyPaymentStatus(currentUser, paymentId));
    }

    @PostMapping(value = "/webhooks/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StripeWebhookResultResponse> handleStripeWebhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String signatureHeader,
            @Size(max = 262_144, message = "Stripe webhook payload must not exceed 262144 characters.")
            @RequestBody String payload) {
        return ApiResponse.success(
                "Stripe webhook processed.",
                stripeWebhookService.handleWebhook(payload, signatureHeader));
    }
}
