package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.account.TokenRequest;
import com.example.aichinese.dto.auth.LinkTokenResponse;
import com.example.aichinese.dto.plan.EmailConfirmationView;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.plan.EmailConfirmationService;

/**
 * Confirming a new account's e-mail address: the public link from the sign-up e-mail (the token is the proof; it
 * shares the per-IP limit of the other e-mailed links) and "Send the link again" for the signed-in owner.
 */
@RestController
public class EmailConfirmationController {

    private final EmailConfirmationService confirmationService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;

    public EmailConfirmationController(
            EmailConfirmationService confirmationService,
            PublicAuthRequestLimiter publicAuthRequestLimiter,
            ClientIpResolver clientIpResolver) {
        this.confirmationService = confirmationService;
        this.publicAuthRequestLimiter = publicAuthRequestLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/api/v1/auth/confirm-signup-email")
    public ApiResponse<LinkTokenResponse> confirm(
            @Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkResetPasswordAllowed(clientIpResolver.resolve(httpRequest));
        return ApiResponse.success("Email address confirmed.",
                new LinkTokenResponse(confirmationService.confirm(request.token())));
    }

    @PostMapping("/api/v1/users/me/email-confirmation")
    public ApiResponse<EmailConfirmationView> resend(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("We sent a new confirmation link.", confirmationService.resend(currentUser));
    }
}
