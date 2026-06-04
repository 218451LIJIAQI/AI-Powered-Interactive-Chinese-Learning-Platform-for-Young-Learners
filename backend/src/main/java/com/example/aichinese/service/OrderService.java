package com.example.aichinese.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommerceMoneyService commerceMoneyService;
    private final CommerceSettingsService commerceSettingsService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CommerceMoneyService commerceMoneyService,
            CommerceSettingsService commerceSettingsService) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.orderItemRepository = Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.commerceMoneyService = Objects.requireNonNull(commerceMoneyService, "commerceMoneyService");
        this.commerceSettingsService = Objects.requireNonNull(commerceSettingsService, "commerceSettingsService");
    }

    @Transactional
    public Order createOrder(StudentProfile student, CheckoutSource source, List<ResolvedPurchasableItem> items) {
        Order order = new Order();
        order.setStudent(student);
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        String suffix = uuid.substring(0, 8) + "-" + uuid.substring(8, 16);
        order.setOrderNumber("ORD-" + suffix);
        order.setCurrencyCode(commerceSettingsService.getDefaultCurrency());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCheckoutSource(source.name());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ResolvedPurchasableItem item : items) {
            totalAmount = commerceMoneyService.add(totalAmount, item.unitPrice());
        }
        order.setTotalAmount(commerceMoneyService.normalizePersistableAmount(
                totalAmount,
                "Order total amount is too large."));
        Order saved = orderRepository.save(order);
        log.info("Order created: orderId={}, orderNumber={}, userId={}, amount={} {}",
                saved.getId(), saved.getOrderNumber(),
                student.getUser().getId(), saved.getTotalAmount(), saved.getCurrencyCode());
        return saved;
    }

    @Transactional
    public List<OrderItem> createOrderItems(Order order, List<ResolvedPurchasableItem> items) {
        List<OrderItem> pending = new ArrayList<>(items.size());
        for (ResolvedPurchasableItem item : items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setItemType(item.itemType().name());
            orderItem.setItemId(item.itemId());
            orderItem.setItemTitleSnapshot(item.title());
            orderItem.setFeatureKey(item.relatedFeatureKey());
            orderItem.setQuantity(1);
            BigDecimal normalizedPrice = commerceMoneyService.normalizePersistableAmount(
                    item.unitPrice(),
                    "Order item price is too large.");
            BigDecimal lineTotal = commerceMoneyService.normalizePersistableAmount(
                    commerceMoneyService.multiply(normalizedPrice, BigDecimal.valueOf(orderItem.getQuantity())),
                    "Order item line total is too large.");
            orderItem.setUnitPrice(normalizedPrice);
            orderItem.setLineTotal(lineTotal);
            orderItem.setTeacher(item.teacher());
            pending.add(orderItem);
        }
        return orderItemRepository.saveAll(pending);
    }
}
