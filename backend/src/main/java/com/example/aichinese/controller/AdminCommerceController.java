package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.commerce.AdminPayoutRequestResponse;
import com.example.aichinese.dto.commerce.AdminPaymentRecordResponse;
import com.example.aichinese.dto.commerce.AdminRefundRequestResponse;
import com.example.aichinese.dto.commerce.CommissionSettingRequest;
import com.example.aichinese.dto.commerce.CommissionSettingResponse;
import com.example.aichinese.dto.commerce.PayoutDecisionRequest;
import com.example.aichinese.dto.commerce.RefundDecisionRequest;
import com.example.aichinese.dto.system.AdminPlatformSettingsResponse;
import com.example.aichinese.dto.system.AdminPlatformSettingsUpdateRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.CommissionService;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.PayoutService;
import com.example.aichinese.service.RefundService;

import jakarta.validation.Valid;

/**
 * Admin-side commerce surface: refund-request review,
 * payout-request review, and platform commission-rate editing.
 *
 * <h2>Approval workflow</h2>
 * <p>{@link com.example.aichinese.service.RefundService} and
 * {@link com.example.aichinese.service.PayoutService} drive the
 * one-way state transitions (PENDING → APPROVED / REJECTED →
 * COMPLETED / PAID). Decisions are audited via
 * {@link com.example.aichinese.service.AuditLogService} and trigger
 * notifications to the affected student / teacher.</p>
 *
 * <h2>Commission rate</h2>
 * <p>{@link com.example.aichinese.service.CommissionService} is the
 * single write path for the {@code platform_commission_rate} system
 * setting; the service rejects values outside [0, 1] and writes an
 * audit entry so changes are reconstructable from logs alone.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCommerceController {

    private final RefundService refundService;
    private final PayoutService payoutService;
    private final CommissionService commissionService;
    private final PaymentService paymentService;
    private final PlatformSettingsService platformSettingsService;

    public AdminCommerceController(
            RefundService refundService,
            PayoutService payoutService,
            CommissionService commissionService,
            PaymentService paymentService,
            PlatformSettingsService platformSettingsService) {
        this.refundService = refundService;
        this.payoutService = payoutService;
        this.commissionService = commissionService;
        this.paymentService = paymentService;
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping("/payments")
    public ApiResponse<PageResponse<AdminPaymentRecordResponse>> getPayments(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success("Payments retrieved successfully.", paymentService.getAllPayments(page, size));
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<AdminPaymentRecordResponse> getPayment(@PathVariable Long paymentId) {
        return ApiResponse.success("Payment retrieved successfully.", paymentService.getAdminPayment(paymentId));
    }

    @GetMapping("/refund-requests")
    public ApiResponse<PageResponse<AdminRefundRequestResponse>> getRefundRequests(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success("Refund requests retrieved successfully.", refundService.getAllRefundRequests(page, size));
    }

    @PostMapping("/refund-requests/{id}/approve")
    public ApiResponse<AdminRefundRequestResponse> approveRefund(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Refund approved successfully.", refundService.approveRefund(currentUser, id));
    }

    @PostMapping("/refund-requests/{id}/reject")
    public ApiResponse<AdminRefundRequestResponse> rejectRefund(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RefundDecisionRequest request) {
        return ApiResponse.success("Refund rejected successfully.", refundService.rejectRefund(currentUser, id, request));
    }

    @PostMapping("/refund-requests/{id}/mark-refunded")
    public ApiResponse<AdminRefundRequestResponse> markRefunded(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Refund marked refunded successfully.", refundService.markRefunded(currentUser, id));
    }

    @GetMapping("/payout-requests")
    public ApiResponse<PageResponse<AdminPayoutRequestResponse>> getPayoutRequests(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success("Payout requests retrieved successfully.", payoutService.getAllPayoutRequests(page, size));
    }

    @PostMapping("/payout-requests/{id}/approve")
    public ApiResponse<AdminPayoutRequestResponse> approvePayout(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Payout approved successfully.", payoutService.approvePayout(currentUser, id));
    }

    @PostMapping("/payout-requests/{id}/reject")
    public ApiResponse<AdminPayoutRequestResponse> rejectPayout(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody PayoutDecisionRequest request) {
        return ApiResponse.success("Payout rejected successfully.", payoutService.rejectPayout(currentUser, id, request));
    }

    @PostMapping("/payout-requests/{id}/mark-paid")
    public ApiResponse<AdminPayoutRequestResponse> markPaid(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Payout marked paid successfully.", payoutService.markPayoutPaid(currentUser, id));
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

    @GetMapping("/system-settings/platform")
    public ApiResponse<AdminPlatformSettingsResponse> getPlatformSettings() {
        return ApiResponse.success(
                "Platform settings retrieved successfully.",
                platformSettingsService.getAdminPlatformSettings());
    }

    @PutMapping("/system-settings/platform")
    public ApiResponse<AdminPlatformSettingsResponse> updatePlatformSettings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminPlatformSettingsUpdateRequest request) {
        return ApiResponse.success(
                "Platform settings updated successfully.",
                platformSettingsService.updateAdminPlatformSettings(currentUser, request));
    }
}
