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
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
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
            orderItem.setUnitPrice(item.unitPrice());
            orderItem.setLineTotal(item.unitPrice());
            orderItem.setTeacher(item.teacher());
            saved.add(orderItemRepository.save(orderItem));
        }
        return saved;
    }
}
