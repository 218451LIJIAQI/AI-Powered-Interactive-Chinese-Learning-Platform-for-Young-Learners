package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.commerce.CartItemRequest;
import com.example.aichinese.dto.commerce.CartResponse;
import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.CheckoutSessionItemRequest;
import com.example.aichinese.dto.commerce.PaymentRecordResponse;
import com.example.aichinese.dto.commerce.RefundRequestCreateRequest;
import com.example.aichinese.dto.commerce.RefundRequestResponse;
import com.example.aichinese.dto.commerce.StudentSubscriptionResponse;
import com.example.aichinese.dto.commerce.SubscriptionPlanResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.CartService;
import com.example.aichinese.service.CheckoutService;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.RefundService;
import com.example.aichinese.service.SubscriptionService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/students")
public class StudentCommerceController {

    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final RefundService refundService;

    public StudentCommerceController(
            CartService cartService,
            CheckoutService checkoutService,
            PaymentService paymentService,
            SubscriptionService subscriptionService,
            RefundService refundService) {
        this.cartService = cartService;
        this.checkoutService = checkoutService;
        this.paymentService = paymentService;
        this.subscriptionService = subscriptionService;
        this.refundService = refundService;
    }

    @GetMapping("/cart")
    public ApiResponse<CartResponse> getCart(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Cart retrieved successfully.", cartService.getCart(currentUser));
    }

    @PostMapping("/cart/items")
    public ApiResponse<CartResponse> addCartItem(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CartItemRequest request) {
        return ApiResponse.success("Cart item added successfully.", cartService.addItem(currentUser, request));
    }

    @DeleteMapping("/cart/items/{cartItemId}")
    public ApiResponse<CartResponse> removeCartItem(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long cartItemId) {
        return ApiResponse.success("Cart item removed successfully.", cartService.removeItem(currentUser, cartItemId));
    }

    @DeleteMapping("/cart/clear")
    public ApiResponse<CartResponse> clearCart(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Cart cleared successfully.", cartService.clearCart(currentUser));
    }

    @PostMapping("/checkout/buy-now")
    public ApiResponse<CheckoutResponse> buyNow(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CheckoutSessionItemRequest request) {
        return ApiResponse.success("Buy now checkout created successfully.", checkoutService.buyNow(currentUser, request));
    }

    @PostMapping("/checkout/cart")
    public ApiResponse<CheckoutResponse> checkoutCart(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Cart checkout created successfully.", checkoutService.checkoutCart(currentUser));
    }

    @GetMapping("/payments")
    public ApiResponse<List<PaymentRecordResponse>> getPayments(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Payments retrieved successfully.", paymentService.getStudentPayments(currentUser));
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentRecordResponse> getPayment(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long paymentId) {
        return ApiResponse.success("Payment retrieved successfully.", paymentService.getStudentPayment(currentUser, paymentId));
    }

    @GetMapping("/subscription")
    public ApiResponse<StudentSubscriptionResponse> getCurrentSubscription(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Current subscription retrieved successfully.",
                subscriptionService.getCurrentStudentSubscription(currentUser));
    }

    @GetMapping("/subscription-plans")
    public ApiResponse<List<SubscriptionPlanResponse>> getSubscriptionPlans() {
        return ApiResponse.success("Subscription plans retrieved successfully.", subscriptionService.getActivePlans());
    }

    @PostMapping("/subscription/checkout")
    public ApiResponse<CheckoutResponse> checkoutSubscription(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CheckoutSessionItemRequest request) {
        return ApiResponse.success(
                "Subscription checkout created successfully.",
                checkoutService.checkoutSubscription(currentUser, request));
    }

    @GetMapping("/refund-requests")
    public ApiResponse<List<RefundRequestResponse>> getRefundRequests(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Refund requests retrieved successfully.",
                refundService.getStudentRefundRequests(currentUser));
    }

    @PostMapping("/refund-requests")
    public ApiResponse<RefundRequestResponse> createRefundRequest(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody RefundRequestCreateRequest request) {
        return ApiResponse.success(
                "Refund request submitted successfully.",
                refundService.createRefundRequest(currentUser, request));
    }
}
