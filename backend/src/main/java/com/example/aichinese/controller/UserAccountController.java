package com.example.aichinese.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.account.AccountSettingsResponse;
import com.example.aichinese.dto.account.DeleteAccountRequest;
import com.example.aichinese.dto.auth.EmailChangeRequest;
import com.example.aichinese.dto.account.EmailChangeResponse;
import com.example.aichinese.dto.account.NotificationPreferences;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.AuthSessionResponse;
import com.example.aichinese.dto.auth.ChangePasswordRequest;
import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.dto.auth.UpdateProfileRequest;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.account.AccountDeletionService;
import com.example.aichinese.service.account.AccountEmailService;
import com.example.aichinese.service.account.AccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * The Account page for every signed-in role: profile, e-mail change, e-mail notification preferences, password change
 * and account deletion (FRAUTH07, FRAUTH08).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserAccountController {

    private final AuthService authService;
    private final AccountService accountService;
    private final AccountEmailService accountEmailService;
    private final AccountDeletionService accountDeletionService;
    private final ClientIpResolver clientIpResolver;
    private final SecurityProperties securityProperties;

    public UserAccountController(
            AuthService authService,
            AccountService accountService,
            AccountEmailService accountEmailService,
            AccountDeletionService accountDeletionService,
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties) {
        this.authService = authService;
        this.accountService = accountService;
        this.accountEmailService = accountEmailService;
        this.accountDeletionService = accountDeletionService;
        this.clientIpResolver = clientIpResolver;
        this.securityProperties = securityProperties;
    }

    @GetMapping("/account")
    public ApiResponse<AccountSettingsResponse> getAccount(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Account settings retrieved successfully.", accountService.settings(currentUser));
    }

    @PutMapping("/profile")
    public ApiResponse<CurrentUserResponse> updateProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success("Profile updated successfully.", authService.updateProfile(currentUser, request));
    }

    @PutMapping("/notification-preferences")
    public ApiResponse<NotificationPreferences> updateNotificationPreferences(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestBody NotificationPreferences request) {
        return ApiResponse.success(
                "Notification preferences saved.",
                accountService.updatePreferences(currentUser, request));
    }

    /** Sends a confirmation link to the new address; the e-mail changes when that link is opened. */
    @PostMapping("/email-change")
    public ApiResponse<EmailChangeResponse> requestEmailChange(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody EmailChangeRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                "Confirmation link sent.",
                accountEmailService.requestChange(
                        currentUser, request.newEmail(), request.currentPassword(), clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        AuthSessionResponse session = authService.changePassword(
                currentUser,
                request,
                clientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return RefreshCookieWriter.sessionResponse(
                securityProperties,
                "Password changed successfully. Other sessions have been signed out.",
                session);
    }

    /** Deletes the signed-in student's or teacher's account and signs it out (the refresh cookie is cleared). */
    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(currentUser, request.confirmEmail());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookieWriter.clearedCookieHeaders(securityProperties))
                .body(ApiResponse.success("Account deleted.", null));
    }
}
