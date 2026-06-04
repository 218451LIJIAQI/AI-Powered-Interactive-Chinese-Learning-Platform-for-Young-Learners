package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

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

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    static final int MAX_CHECKOUT_ITEMS = CartService.MAX_CART_ITEMS;

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
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.cartService = Objects.requireNonNull(cartService, "cartService");
        this.purchasableItemService = Objects.requireNonNull(purchasableItemService, "purchasableItemService");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService");
        this.orderService = Objects.requireNonNull(orderService, "orderService");
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService");
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
    }

    @Transactional
    public CheckoutResponse checkoutCart(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        List<CartItem> cartItems = cartService.getCartItems(currentUser);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Cart is empty.");
        }
        if (cartItems.size() > MAX_CHECKOUT_ITEMS) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Checkout can contain at most " + MAX_CHECKOUT_ITEMS + " items.");
        }

        List<ResolvedPurchasableItem> resolvedItems = cartItems.stream()
                .map(this::resolveCartCheckoutItem)
                .toList();
        return createCheckout(currentUser, student, CheckoutSource.CART, resolvedItems);
    }

    @Transactional
    public CheckoutResponse buyNow(SecurityUserDetails currentUser, CheckoutSessionItemRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Checkout item request is required.");
        }
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
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one item is required for checkout.");
        }
        if (items.size() > MAX_CHECKOUT_ITEMS) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Checkout can contain at most " + MAX_CHECKOUT_ITEMS + " items.");
        }
        Set<String> seenItems = new HashSet<>(items.size());
        for (ResolvedPurchasableItem item : items) {
            if (item == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Checkout item must not be null.");
            }
            if (item.itemType() == null || item.itemId() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Checkout item is missing its identity.");
            }
            if (!seenItems.add(item.itemType().name() + ":" + item.itemId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Checkout contains duplicate items.");
            }
            if (item.unitPrice() == null || item.unitPrice().signum() < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Checkout item has an invalid price.");
            }
            if (entitlementService.hasAccessToPurchasableItem(student, item)) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "You already have access to: " + item.title());
            }
        }

        Order order = orderService.createOrder(student, source, items);
        List<OrderItem> orderItems = orderService.createOrderItems(order, items);
        PaymentRecord paymentRecord = paymentService.createPendingPayment(order, student);

        if (order.getTotalAmount().signum() <= 0) {
            paymentService.markPaymentPaid(paymentRecord, "FREE", "FREE_UNLOCK");
            log.info("Free checkout completed: orderId={}, userId={}, items={}",
                    order.getId(), student.getUser().getId(), items.size());
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

        String successUrl = UriComponentsBuilder
                .fromUriString(paymentProperties.successUrl())
                .queryParam("paymentId", paymentRecord.getId())
                .build().toUriString();
        String cancelUrl = UriComponentsBuilder
                .fromUriString(paymentProperties.cancelUrl())
                .queryParam("paymentId", paymentRecord.getId())
                .queryParam("cancelled", true)
                .build().toUriString();

        log.info("Starting paid checkout: orderId={}, paymentId={}, userId={}, amount={}",
                order.getId(), paymentRecord.getId(),
                student.getUser().getId(), order.getTotalAmount());

        return paymentService.startCheckout(
                currentUser, student, order, paymentRecord, orderItems,
                successUrl, cancelUrl);
    }

    private ResolvedPurchasableItem resolveCartCheckoutItem(CartItem cartItem) {
        if (cartItem == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Cart contains an invalid item.");
        }
        ResolvedPurchasableItem currentItem = purchasableItemService.resolve(
                cartItem.getItemType(), cartItem.getItemId(), null);
        BigDecimal capturedPrice = cartItem.getUnitPrice();
        if (currentItem.freeItem() || capturedPrice == null || capturedPrice.signum() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "A cart item is no longer eligible for paid checkout. Please remove it and try again.");
        }
        return new ResolvedPurchasableItem(
                currentItem.itemType(),
                currentItem.itemId(),
                currentItem.title(),
                capturedPrice,
                currentItem.currencyCode(),
                currentItem.teacher(),
                false,
                currentItem.relatedFeatureKey());
    }
}
