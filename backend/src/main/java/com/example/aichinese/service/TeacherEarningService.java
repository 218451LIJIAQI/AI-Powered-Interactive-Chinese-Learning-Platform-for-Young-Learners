package com.example.aichinese.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.TeacherEarningRepository;

@Service
public class TeacherEarningService {

    private final OrderItemRepository orderItemRepository;
    private final TeacherEarningRepository teacherEarningRepository;
    private final CommerceSettingsService commerceSettingsService;
    private final CommerceMoneyService commerceMoneyService;

    public TeacherEarningService(
            OrderItemRepository orderItemRepository,
            TeacherEarningRepository teacherEarningRepository,
            CommerceSettingsService commerceSettingsService,
            CommerceMoneyService commerceMoneyService) {
        this.orderItemRepository = orderItemRepository;
        this.teacherEarningRepository = teacherEarningRepository;
        this.commerceSettingsService = commerceSettingsService;
        this.commerceMoneyService = commerceMoneyService;
    }

    @Transactional
    public void createEarningsForOrder(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        BigDecimal commissionRate = commerceSettingsService.getPlatformCommissionRate();

        for (OrderItem orderItem : orderItems) {
            if (orderItem.getTeacher() == null || teacherEarningRepository.findByOrderItemId(orderItem.getId()).isPresent()) {
                continue;
            }

            TeacherEarning earning = new TeacherEarning();
            earning.setTeacher(orderItem.getTeacher());
            earning.setOrderItem(orderItem);
            earning.setGrossAmount(commerceMoneyService.normalize(orderItem.getLineTotal()));
            earning.setCommissionAmount(commerceMoneyService.percentage(orderItem.getLineTotal(), commissionRate));
            earning.setNetAmount(commerceMoneyService.subtract(earning.getGrossAmount(), earning.getCommissionAmount()));
            earning.setEarningStatus("AVAILABLE");
            teacherEarningRepository.save(earning);
        }
    }

    @Transactional
    public void markRefundedForOrder(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        for (OrderItem orderItem : orderItems) {
            teacherEarningRepository.findByOrderItemId(orderItem.getId())
                    .ifPresent(earning -> earning.setEarningStatus("REFUNDED"));
        }
    }
}
