package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.commerce.CommissionSettingRequest;
import com.example.aichinese.dto.commerce.CommissionSettingResponse;
import com.example.aichinese.dto.commerce.PaymentRecordResponse;
import com.example.aichinese.dto.commerce.PayoutDecisionRequest;
import com.example.aichinese.dto.commerce.PayoutRequestResponse;
import com.example.aichinese.dto.commerce.RefundDecisionRequest;
import com.example.aichinese.dto.commerce.RefundRequestResponse;
import com.example.aichinese.dto.commerce.SubscriptionPlanRequest;
import com.example.aichinese.dto.commerce.SubscriptionPlanResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.CommissionService;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.PayoutService;
import com.example.aichinese.service.RefundService;
import com.example.aichinese.service.SubscriptionService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCommerceController {

    private final RefundService refundService;
    private final PayoutService payoutService;
    private final SubscriptionService subscriptionService;
    private final CommissionService commissionService;
    private final PaymentService paymentService;

    public AdminCommerceController(
            RefundService refundService,
            PayoutService payoutService,
            SubscriptionService subscriptionService,
            CommissionService commissionService,
            PaymentService paymentService) {
        this.refundService = refundService;
        this.payoutService = payoutService;
        this.subscriptionService = subscriptionService;
        this.commissionService = commissionService;
        this.paymentService = paymentService;
    }

    @GetMapping("/payments")
    public ApiResponse<List<PaymentRecordResponse>> getPayments() {
        return ApiResponse.success("Payments retrieved successfully.", paymentService.getAllPayments());
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentRecordResponse> getPayment(@PathVariable Long paymentId) {
        return ApiResponse.success("Payment retrieved successfully.", paymentService.getAdminPayment(paymentId));
    }

    @GetMapping("/refund-requests")
    public ApiResponse<List<RefundRequestResponse>> getRefundRequests() {
        return ApiResponse.success("Refund requests retrieved successfully.", refundService.getAllRefundRequests());
    }

    @PostMapping("/refund-requests/{id}/approve")
    public ApiResponse<RefundRequestResponse> approveRefund(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Refund approved successfully.", refundService.approveRefund(currentUser, id));
    }

    @PostMapping("/refund-requests/{id}/reject")
    public ApiResponse<RefundRequestResponse> rejectRefund(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RefundDecisionRequest request) {
        return ApiResponse.success("Refund rejected successfully.", refundService.rejectRefund(currentUser, id, request));
    }

    @PostMapping("/refund-requests/{id}/mark-refunded")
    public ApiResponse<RefundRequestResponse> markRefunded(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Refund marked refunded successfully.", refundService.markRefunded(currentUser, id));
    }

    @GetMapping("/payout-requests")
    public ApiResponse<List<PayoutRequestResponse>> getPayoutRequests() {
        return ApiResponse.success("Payout requests retrieved successfully.", payoutService.getAllPayoutRequests());
    }

    @PostMapping("/payout-requests/{id}/approve")
    public ApiResponse<PayoutRequestResponse> approvePayout(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Payout approved successfully.", payoutService.approvePayout(currentUser, id));
    }

    @PostMapping("/payout-requests/{id}/reject")
    public ApiResponse<PayoutRequestResponse> rejectPayout(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody PayoutDecisionRequest request) {
        return ApiResponse.success("Payout rejected successfully.", payoutService.rejectPayout(currentUser, id, request));
    }

    @PostMapping("/payout-requests/{id}/mark-paid")
    public ApiResponse<PayoutRequestResponse> markPaid(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Payout marked paid successfully.", payoutService.markPayoutPaid(currentUser, id));
    }

    @GetMapping("/subscription-plans")
    public ApiResponse<List<SubscriptionPlanResponse>> getSubscriptionPlans() {
        return ApiResponse.success("Subscription plans retrieved successfully.", subscriptionService.getAllPlans());
    }

    @PostMapping("/subscription-plans")
    public ApiResponse<SubscriptionPlanResponse> createSubscriptionPlan(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody SubscriptionPlanRequest request) {
        return ApiResponse.success("Subscription plan created successfully.",
                subscriptionService.createPlan(currentUser, request));
    }

    @PutMapping("/subscription-plans/{id}")
    public ApiResponse<SubscriptionPlanResponse> updateSubscriptionPlan(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionPlanRequest request) {
        return ApiResponse.success("Subscription plan updated successfully.",
                subscriptionService.updatePlan(currentUser, id, request));
    }

    @PatchMapping("/subscription-plans/{id}/activate")
    public ApiResponse<SubscriptionPlanResponse> activateSubscriptionPlan(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Subscription plan activated successfully.",
                subscriptionService.setPlanActive(currentUser, id, true));
    }

    @PatchMapping("/subscription-plans/{id}/deactivate")
    public ApiResponse<SubscriptionPlanResponse> deactivateSubscriptionPlan(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Subscription plan deactivated successfully.",
                subscriptionService.setPlanActive(currentUser, id, false));
    }

    @GetMapping("/system-settings/commission")
    public ApiResponse<CommissionSettingResponse> getCommissionSetting() {
        return ApiResponse.success("Commission setting retrieved successfully.", commissionService.getCommissionSetting());
    }

    @PutMapping("/system-settings/commission")
    public ApiResponse<CommissionSettingResponse> updateCommissionSetting(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CommissionSettingRequest request) {
        return ApiResponse.success(
                "Commission setting updated successfully.",
                commissionService.updateCommissionSetting(currentUser, request));
    }
}
