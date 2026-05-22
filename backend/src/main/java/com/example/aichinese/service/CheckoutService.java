package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.CartItemResponse;
import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.CheckoutSessionItemRequest;
import com.example.aichinese.dto.commerce.CheckoutSessionRequest.CheckoutSource;
import com.example.aichinese.entity.CartItem;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

@Service
public class CheckoutService {

    private final StudentLearningAccessService studentLearningAccessService;
    private final CartService cartService;
    private final PurchasableItemService purchasableItemService;
    private final EntitlementService entitlementService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;

    public CheckoutService(
            StudentLearningAccessService studentLearningAccessService,
            CartService cartService,
            PurchasableItemService purchasableItemService,
            EntitlementService entitlementService,
            OrderService orderService,
            PaymentService paymentService,
            PaymentProperties paymentProperties) {
        this.studentLearningAccessService = studentLearningAccessService;
        this.cartService = cartService;
        this.purchasableItemService = purchasableItemService;
        this.entitlementService = entitlementService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
    }

    @Transactional
    public CheckoutResponse checkoutCart(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        List<CartItem> cartItems = cartService.getCartItems(currentUser);
        if (cartItems.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Cart is empty.");
        }

        List<ResolvedPurchasableItem> resolvedItems = cartItems.stream()
                .map(item -> purchasableItemService.resolve(item.getItemType(), item.getItemId(), null))
                .toList();
        return createCheckout(currentUser, student, CheckoutSource.CART, resolvedItems);
    }

    @Transactional
    public CheckoutResponse buyNow(SecurityUserDetails currentUser, CheckoutSessionItemRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        ResolvedPurchasableItem resolvedItem = purchasableItemService.resolve(
                request.itemType(),
                request.itemId(),
                request.featureKey());
        return createCheckout(currentUser, student, CheckoutSource.BUY_NOW, List.of(resolvedItem));
    }

    private CheckoutResponse createCheckout(
            SecurityUserDetails currentUser,
            StudentProfile student,
            CheckoutSource source,
            List<ResolvedPurchasableItem> items) {
        if (items.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one item is required for checkout.");
        }
        for (ResolvedPurchasableItem item : items) {
            if (entitlementService.hasAccessToPurchasableItem(student, item)) {
                throw new ResponseStatusException(BAD_REQUEST, "You already have access to this item.");
            }
        }

        Order order = orderService.createOrder(student, source, items);
        List<OrderItem> orderItems = orderService.createOrderItems(order, items);
        PaymentRecord paymentRecord = paymentService.createPendingPayment(order, student);

        if (order.getTotalAmount().signum() <= 0) {
            paymentService.markPaymentPaid(paymentRecord, "FREE", "FREE_UNLOCK");
            return new CheckoutResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    paymentRecord.getId(),
                    paymentRecord.getPaymentStatus(),
                    paymentRecord.getAmount(),
                    paymentRecord.getCurrencyCode(),
                    null,
                    paymentRecord.getProviderName(),
                    paymentRecord.getProviderStatus(),
                    true,
                    "Free item unlocked successfully.",
                    orderItems.stream()
                            .map(item -> new CartItemResponse(
                                    null,
                                    item.getItemType(),
                                    item.getItemId(),
                                    item.getItemTitleSnapshot(),
                                    item.getUnitPrice(),
                                    order.getCurrencyCode(),
                                    item.getQuantity(),
                                    item.getLineTotal(),
                                    item.getTeacher() != null))
                            .toList());
        }

        return paymentService.startCheckout(
                currentUser,
                student,
                order,
                paymentRecord,
                orderItems,
                paymentProperties.successUrl() + "?paymentId=" + paymentRecord.getId(),
                paymentProperties.cancelUrl() + "?paymentId=" + paymentRecord.getId() + "&cancelled=true");
    }
}
