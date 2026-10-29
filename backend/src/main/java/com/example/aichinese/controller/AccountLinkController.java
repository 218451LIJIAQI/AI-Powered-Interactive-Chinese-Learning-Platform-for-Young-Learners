package com.example.aichinese.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.account.TokenRequest;
import com.example.aichinese.dto.auth.AcceptAdminInviteRequest;
import com.example.aichinese.dto.auth.LinkTokenResponse;
import com.example.aichinese.dto.auth.RegistrationResponse;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.account.AccountEmailService;
import com.example.aichinese.service.admin.AdminInviteService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * The links MandarinSpark e-mails (password reset, e-mail change confirmation, admin invite). Public: the token in the
 * link is the proof; every call shares the per-IP reset-password limit.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AccountLinkController {

    private final AuthService authService;
    private final AccountEmailService accountEmailService;
    private final AdminInviteService adminInviteService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;

    public AccountLinkController(
            AuthService authService,
            AccountEmailService accountEmailService,
            AdminInviteService adminInviteService,
            PublicAuthRequestLimiter publicAuthRequestLimiter,
            ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.accountEmailService = accountEmailService;
        this.adminInviteService = adminInviteService;
        this.publicAuthRequestLimiter = publicAuthRequestLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    /** Whether a reset link still works (the reset page shows the account address or the expired state). */
    @PostMapping("/reset-password/check")
    public ApiResponse<LinkTokenResponse> checkResetLink(
            @Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        limit(httpRequest);
        return ApiResponse.success("Reset link is valid.", authService.checkResetToken(request.token()));
    }

    /** Opens an e-mail change link: the new address replaces the old one and every session is signed out. */
    @PostMapping("/confirm-email")
    public ApiResponse<LinkTokenResponse> confirmEmail(
            @Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        limit(httpRequest);
        return ApiResponse.success(
                "Email address changed.",
                new LinkTokenResponse(accountEmailService.confirm(request.token())));
    }

    @PostMapping("/admin-invite/check")
    public ApiResponse<LinkTokenResponse> checkAdminInvite(
            @Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        limit(httpRequest);
        return ApiResponse.success("Invite link is valid.", adminInviteService.check(request.token()));
    }

    @PostMapping("/admin-invite/accept")
    public ApiResponse<RegistrationResponse> acceptAdminInvite(
            @Valid @RequestBody AcceptAdminInviteRequest request, HttpServletRequest httpRequest) {
        limit(httpRequest);
        return ApiResponse.success("Admin account created.", adminInviteService.accept(request));
    }

    private void limit(HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkResetPasswordAllowed(clientIpResolver.resolve(httpRequest));
    }
}
