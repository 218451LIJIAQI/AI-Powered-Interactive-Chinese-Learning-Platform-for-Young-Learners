package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.commerce.PaymentRecordResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.PaymentService;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/status/{paymentId}")
    public ApiResponse<PaymentRecordResponse> getPaymentStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long paymentId) {
        return ApiResponse.success(
                "Payment status retrieved successfully.",
                paymentService.verifyPaymentStatus(currentUser, paymentId));
    }
}
