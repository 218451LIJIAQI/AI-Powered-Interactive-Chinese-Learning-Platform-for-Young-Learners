package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PaymentProperties;
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
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final PaymentProperties paymentProperties;

    public CheckoutService(
            StudentLearningAccessService studentLearningAccessService,
            CartService cartService,
            PurchasableItemService purchasableItemService,
            OrderService orderService,
            PaymentService paymentService,
            SubscriptionService subscriptionService,
            PaymentProperties paymentProperties) {
        this.studentLearningAccessService = studentLearningAccessService;
        this.cartService = cartService;
        this.purchasableItemService = purchasableItemService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.subscriptionService = subscriptionService;
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

    @Transactional
    public CheckoutResponse checkoutSubscription(SecurityUserDetails currentUser, CheckoutSessionItemRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        ResolvedPurchasableItem resolvedItem = purchasableItemService.resolve(
                request.itemType(),
                request.itemId(),
                request.featureKey());
        return createCheckout(currentUser, student, CheckoutSource.SUBSCRIPTION, List.of(resolvedItem));
    }

    private CheckoutResponse createCheckout(
            SecurityUserDetails currentUser,
            StudentProfile student,
            CheckoutSource source,
            List<ResolvedPurchasableItem> items) {
        if (items.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one item is required for checkout.");
        }

        Order order = orderService.createOrder(student, source, items);
        List<OrderItem> orderItems = orderService.createOrderItems(order, items);
        PaymentRecord paymentRecord = paymentService.createPendingPayment(order, student);
        if (source == CheckoutSource.SUBSCRIPTION && !items.isEmpty()) {
            subscriptionService.createPendingSubscription(student, items.get(0).itemId(), paymentRecord);
        }

        if (order.getTotalAmount().signum() <= 0) {
            paymentService.markPaymentPaid(paymentRecord, "FREE", "FREE_UNLOCK");
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
