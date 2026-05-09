package com.example.aichinese.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.commerce.SubscriptionPlanResponse;
import com.example.aichinese.service.SubscriptionService;

@RestController
@RequestMapping("/api/v1/subscription-plans")
public class SubscriptionPlanController {

    private final SubscriptionService subscriptionService;

    public SubscriptionPlanController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public ApiResponse<List<SubscriptionPlanResponse>> getSubscriptionPlans() {
        return ApiResponse.success("Subscription plans retrieved successfully.", subscriptionService.getActivePlans());
    }
}
