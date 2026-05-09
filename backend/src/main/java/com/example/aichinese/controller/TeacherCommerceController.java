package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.commerce.PayoutRequestCreateRequest;
import com.example.aichinese.dto.commerce.PayoutRequestResponse;
import com.example.aichinese.dto.commerce.TeacherEarningResponse;
import com.example.aichinese.dto.commerce.TeacherEarningSummaryResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.PayoutService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherCommerceController {

    private final PayoutService payoutService;

    public TeacherCommerceController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping("/earnings")
    public ApiResponse<List<TeacherEarningResponse>> getEarnings(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Teacher earnings retrieved successfully.", payoutService.getTeacherEarnings(currentUser));
    }

    @GetMapping("/earnings/summary")
    public ApiResponse<TeacherEarningSummaryResponse> getEarningsSummary(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher earnings summary retrieved successfully.",
                payoutService.getTeacherEarningSummary(currentUser));
    }

    @GetMapping("/payout-requests")
    public ApiResponse<List<PayoutRequestResponse>> getPayoutRequests(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher payout requests retrieved successfully.",
                payoutService.getTeacherPayoutRequests(currentUser));
    }

    @PostMapping("/payout-requests")
    public ApiResponse<PayoutRequestResponse> createPayoutRequest(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PayoutRequestCreateRequest request) {
        return ApiResponse.success(
                "Teacher payout request submitted successfully.",
                payoutService.createPayoutRequest(currentUser, request));
    }
}
