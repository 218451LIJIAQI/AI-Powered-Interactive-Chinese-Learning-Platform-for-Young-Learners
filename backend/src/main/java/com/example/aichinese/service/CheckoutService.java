package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import java.util.Objects;

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

/**
 * Drives the cart-to-order conversion: validates the cart contents,
 * creates the {@link Order} + {@link OrderItem} rows, and asks
 * {@link PaymentService} to produce a provider redirect URL.
 *
 * <h2>Two checkout sources</h2>
 * <p>The student can check out from their persisted cart
 * ({@link com.example.aichinese.dto.commerce.CheckoutSessionRequest.CheckoutSource#CART})
 * or directly with an ad-hoc item list (the
 * {@code DIRECT} buy-now path). Both flows funnel through the same
 * validation + order creation logic to keep the financial path
 * uniform.</p>
 *
 * <h2>Ownership re-check</h2>
 * <p>Even though {@link CartService} rejects already-owned items at
 * add-time, the entitlement check is repeated here at checkout in case
 * a concurrent payment completed in another tab — preventing
 * double-purchase of the same content.</p>
 *
 * <h2>Currency mode</h2>
 * <p>The configured payment-provider mode (live / test / mock) is
 * surfaced through {@link PaymentProperties} so the front-end can
 * show the appropriate banner; the actual provider redirect URL is
 * built by {@link PaymentService}.</p>
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

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
}
