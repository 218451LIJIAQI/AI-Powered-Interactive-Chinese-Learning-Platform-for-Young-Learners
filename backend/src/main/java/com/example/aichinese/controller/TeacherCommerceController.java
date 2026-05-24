package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.commerce.PayoutRequestCreateRequest;
import com.example.aichinese.dto.commerce.TeacherEarningResponse;
import com.example.aichinese.dto.commerce.TeacherEarningSummaryResponse;
import com.example.aichinese.dto.commerce.TeacherPayoutRequestResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.PayoutService;

import jakarta.validation.Valid;

/**
 * Teacher-side commerce REST surface: earnings ledger, available
 * balance, and payout-request lifecycle (file → view → cancel).
 *
 * <p>Earnings are only readable by their owning teacher; the
 * service layer applies the row-level filter so a forged path id
 * cannot expose another teacher's ledger. Payout creation is
 * gated against the teacher's currently AVAILABLE earnings
 * (see {@link com.example.aichinese.service.PayoutService}) so a
 * teacher cannot request more than the platform has reconciled.</p>
 *
 * <p>The admin-side approval / rejection surface for payout
 * requests lives on {@link AdminCommerceController}.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherCommerceController {

    private final PayoutService payoutService;

    public TeacherCommerceController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping("/earnings")
    public ApiResponse<PageResponse<TeacherEarningResponse>> getEarnings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success("Teacher earnings retrieved successfully.", payoutService.getTeacherEarnings(currentUser, page, size));
    }

    @GetMapping("/earnings/summary")
    public ApiResponse<TeacherEarningSummaryResponse> getEarningsSummary(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher earnings summary retrieved successfully.",
                payoutService.getTeacherEarningSummary(currentUser));
    }

    @GetMapping("/payout-requests")
    public ApiResponse<PageResponse<TeacherPayoutRequestResponse>> getPayoutRequests(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher payout requests retrieved successfully.",
                payoutService.getTeacherPayoutRequests(currentUser, page, size));
    }

    @PostMapping("/payout-requests")
    public ApiResponse<TeacherPayoutRequestResponse> createPayoutRequest(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PayoutRequestCreateRequest request) {
        return ApiResponse.success(
                "Teacher payout request submitted successfully.",
                payoutService.createPayoutRequest(currentUser, request));
    }
}
