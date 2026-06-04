package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.commerce.CartItemRequest;
import com.example.aichinese.dto.commerce.CartResponse;
import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.CheckoutSessionItemRequest;
import com.example.aichinese.dto.commerce.RefundRequestCreateRequest;
import com.example.aichinese.dto.commerce.StudentRefundRequestResponse;
import com.example.aichinese.dto.commerce.StudentPaymentRecordResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.CartService;
import com.example.aichinese.service.CheckoutService;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.RefundService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/students")
public class StudentCommerceController {

    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final RefundService refundService;

    public StudentCommerceController(
            CartService cartService,
            CheckoutService checkoutService,
            PaymentService paymentService,
            RefundService refundService) {
        this.cartService = cartService;
        this.checkoutService = checkoutService;
        this.paymentService = paymentService;
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
    public ApiResponse<PageResponse<StudentPaymentRecordResponse>> getPayments(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success("Payments retrieved successfully.", paymentService.getStudentPayments(currentUser, page, size));
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<StudentPaymentRecordResponse> getPayment(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long paymentId) {
        return ApiResponse.success("Payment retrieved successfully.", paymentService.getStudentPayment(currentUser, paymentId));
    }

    @GetMapping("/refund-requests")
    public ApiResponse<PageResponse<StudentRefundRequestResponse>> getRefundRequests(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Refund requests retrieved successfully.",
                refundService.getStudentRefundRequests(currentUser, page, size));
    }

    @PostMapping("/refund-requests")
    public ApiResponse<StudentRefundRequestResponse> createRefundRequest(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody RefundRequestCreateRequest request) {
        return ApiResponse.success(
                "Refund request submitted successfully.",
                refundService.createRefundRequest(currentUser, request));
    }
}
