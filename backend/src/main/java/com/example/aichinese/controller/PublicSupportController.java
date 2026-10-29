package com.example.aichinese.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.support.SupportRequestCreatedResponse;
import com.example.aichinese.dto.support.VisitorSupportRequest;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.service.support.SupportService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** "Send us a message" on the public Help page: a visitor's support request, answered by e-mail. Rate-limited. */
@RestController
@RequestMapping("/api/v1/public/support")
public class PublicSupportController {

    private final SupportService supportService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;

    public PublicSupportController(
            SupportService supportService,
            PublicAuthRequestLimiter publicAuthRequestLimiter,
            ClientIpResolver clientIpResolver) {
        this.supportService = supportService;
        this.publicAuthRequestLimiter = publicAuthRequestLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping
    public ApiResponse<SupportRequestCreatedResponse> send(
            @Valid @RequestBody VisitorSupportRequest request,
            HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkSupportMessageAllowed(request.email(), clientIpResolver.resolve(httpRequest));
        return ApiResponse.success("Message sent.", supportService.createForVisitor(request));
    }
}
