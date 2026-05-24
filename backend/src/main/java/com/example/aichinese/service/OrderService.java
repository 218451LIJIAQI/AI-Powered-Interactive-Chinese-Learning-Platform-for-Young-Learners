package com.example.aichinese.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.commerce.CheckoutSessionRequest.CheckoutSource;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.OrderRepository;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

/**
 * Persists {@link Order} and {@link OrderItem} rows representing a
 * committed checkout, computing line totals and currency through
 * {@link CommerceMoneyService}/{@link CommerceSettingsService}.
 *
 * <p>Orders are created in {@link PaymentStatus#PENDING}; the
 * transition to {@code SUCCESS}/{@code FAILED} happens later in
 * {@link PaymentService} once the provider confirms the result.
 * Each order is keyed by a server-generated {@link UUID} so the
 * external order number is never guessable.</p>
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommerceMoneyService commerceMoneyService;
    private final CommerceSettingsService commerceSettingsService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CommerceMoneyService commerceMoneyService,
            CommerceSettingsService commerceSettingsService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.commerceMoneyService = commerceMoneyService;
        this.commerceSettingsService = commerceSettingsService;
    }

    @Transactional
    public Order createOrder(StudentProfile student, CheckoutSource source, List<ResolvedPurchasableItem> items) {
        Order order = new Order();
        order.setStudent(student);
        // The previous "ORD-" + 8-hex-char UUID prefix carried only 32 bits of
        // entropy (~50% chance of collision after ~65K orders, per the
        // birthday paradox). Because the orderNumber column is UNIQUE, a
        // collision surfaces as a 500 to the student during checkout.
        // Lifting the random suffix to a hyphenated 16-hex pattern raises
        // entropy to 64 bits, which stays birthday-safe past 4B orders, while
        // being short enough for a human-readable "ORD-XXXXXXXX-XXXXXXXX"
        // identifier in receipts and admin views.
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        String suffix = uuid.substring(0, 8) + "-" + uuid.substring(8, 16);
        order.setOrderNumber("ORD-" + suffix);
        order.setCurrencyCode(commerceSettingsService.getDefaultCurrency());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCheckoutSource(source.name());

        order.setTotalAmount(commerceMoneyService.normalize(
                items.stream()
                        .map(ResolvedPurchasableItem::unitPrice)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)));
        return orderRepository.save(order);
    }

    @Transactional
    public List<OrderItem> createOrderItems(Order order, List<ResolvedPurchasableItem> items) {
        List<OrderItem> saved = new java.util.ArrayList<>();
        for (ResolvedPurchasableItem item : items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setItemType(item.itemType().name());
            orderItem.setItemId(item.itemId());
            orderItem.setItemTitleSnapshot(item.title());
            orderItem.setFeatureKey(item.relatedFeatureKey());
            orderItem.setQuantity(1);
            orderItem.setUnitPrice(commerceMoneyService.normalize(item.unitPrice()));
            orderItem.setLineTotal(commerceMoneyService.normalize(item.unitPrice()));
            orderItem.setTeacher(item.teacher());
            saved.add(orderItemRepository.save(orderItem));
        }
        return saved;
    }
}
