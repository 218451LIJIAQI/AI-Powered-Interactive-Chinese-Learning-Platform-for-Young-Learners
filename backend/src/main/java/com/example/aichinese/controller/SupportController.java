package com.example.aichinese.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.support.CreateSupportRequest;
import com.example.aichinese.dto.support.SupportRequestCreatedResponse;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.support.SupportService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/** A learner's "Report a problem" (for example under a class video that does not load); support replies by e-mail. */
@RestController
@RequestMapping("/api/v1/users/me/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;
    private final PublicAuthRequestLimiter rateLimiter;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SupportRequestCreatedResponse> create(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @ModelAttribute CreateSupportRequest request,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot) {
        rateLimiter.checkLearnerSupportAllowed(currentUser.getUserId());
        return ApiResponse.success("Support request sent.", supportService.create(currentUser, request, screenshot));
    }
}
