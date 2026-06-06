package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherCommerceController {

    private final PayoutService payoutService;

    public TeacherCommerceController(PayoutService payoutService) {
        this.payoutService = Objects.requireNonNull(payoutService, "payoutService");
    }

    @GetMapping("/earnings")
    public ApiResponse<PageResponse<TeacherEarningResponse>> getEarnings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher earnings retrieved successfully.",
                payoutService.getTeacherEarnings(currentUser, page, size));
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
