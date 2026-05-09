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
import com.example.aichinese.dto.communication.CreateSupportTicketRequest;
import com.example.aichinese.dto.communication.SupportTicketDetailResponse;
import com.example.aichinese.dto.communication.SupportTicketMessageRequest;
import com.example.aichinese.dto.communication.SupportTicketResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.SupportTicketService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/support-tickets")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @PostMapping
    public ApiResponse<SupportTicketResponse> createTicket(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateSupportTicketRequest request) {
        return ApiResponse.success(
                "Support ticket created successfully.",
                supportTicketService.createTicket(currentUser, request));
    }

    @GetMapping("/my")
    public ApiResponse<List<SupportTicketResponse>> getMyTickets(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Support tickets retrieved successfully.",
                supportTicketService.getMyTickets(currentUser));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupportTicketDetailResponse> getMyTicket(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Support ticket retrieved successfully.",
                supportTicketService.getMyTicket(currentUser, id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<SupportTicketDetailResponse> addMessage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SupportTicketMessageRequest request) {
        return ApiResponse.success(
                "Support ticket reply sent successfully.",
                supportTicketService.addMyMessage(currentUser, id, request));
    }
}
