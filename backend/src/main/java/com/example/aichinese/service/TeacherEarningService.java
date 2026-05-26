package com.example.aichinese.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.entity.enums.TeacherEarningStatus;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.TeacherEarningRepository;

/**
 * Creates {@link TeacherEarning} ledger rows when an order is paid
 * and marks them refunded when a refund is processed.
 *
 * <p>Earnings are computed line-by-line: gross = line total, commission
 * = gross × {@code platform_commission_rate}, net = gross − commission.
 * Currency arithmetic flows through {@link CommerceMoneyService} so
 * the math matches the rest of the commerce stack. Idempotent: an
 * earning row is only created once per {@link OrderItem}; subsequent
 * webhook retries see the existing row and skip.</p>
 */
@Service
public class TeacherEarningService {

    private static final Logger log = LoggerFactory.getLogger(TeacherEarningService.class);

    private final OrderItemRepository orderItemRepository;
    private final TeacherEarningRepository teacherEarningRepository;
    private final CommerceSettingsService commerceSettingsService;
    private final CommerceMoneyService commerceMoneyService;

    public TeacherEarningService(
            OrderItemRepository orderItemRepository,
            TeacherEarningRepository teacherEarningRepository,
            CommerceSettingsService commerceSettingsService,
            CommerceMoneyService commerceMoneyService) {
        this.orderItemRepository = Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.teacherEarningRepository = Objects.requireNonNull(teacherEarningRepository, "teacherEarningRepository");
        this.commerceSettingsService = Objects.requireNonNull(commerceSettingsService, "commerceSettingsService");
        this.commerceMoneyService = Objects.requireNonNull(commerceMoneyService, "commerceMoneyService");
    }

    @Transactional
    public void createEarningsForOrder(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        BigDecimal commissionRate = commerceSettingsService.getPlatformCommissionRate();

        int created = 0;
        for (OrderItem orderItem : orderItems) {
            if (orderItem.getTeacher() == null || teacherEarningRepository.findByOrderItemId(orderItem.getId()).isPresent()) {
                continue;
            }

            TeacherEarning earning = new TeacherEarning();
            earning.setTeacher(orderItem.getTeacher());
            earning.setOrderItem(orderItem);
            earning.setGrossAmount(commerceMoneyService.normalizePersistableAmount(
                    orderItem.getLineTotal(),
                    "Order item amount is too large for teacher earning settlement."));
            earning.setCommissionAmount(commerceMoneyService.normalizePersistableAmount(
                    commerceMoneyService.percentage(orderItem.getLineTotal(), commissionRate),
                    "Commission amount is too large for teacher earning settlement."));
            earning.setNetAmount(commerceMoneyService.normalizePersistableAmount(
                    commerceMoneyService.subtract(earning.getGrossAmount(), earning.getCommissionAmount()),
                    "Net earning amount is too large for teacher earning settlement."));
            earning.setEarningStatus(TeacherEarningStatus.AVAILABLE);
            teacherEarningRepository.save(earning);
            created++;
        }
        if (created > 0) {
            log.info("Created {} teacher earning(s) for orderId={}", created, orderId);
        }
    }

    @Transactional
    public void markRefundedForOrder(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        int refunded = 0;
        for (OrderItem orderItem : orderItems) {
            if (teacherEarningRepository.findByOrderItemId(orderItem.getId())
                    .map(earning -> {
                        earning.setEarningStatus(TeacherEarningStatus.REFUNDED);
                        return true;
                    }).orElse(false)) {
                refunded++;
            }
        }
        if (refunded > 0) {
            log.info("Marked {} teacher earning(s) as REFUNDED for orderId={}", refunded, orderId);
        }
    }
}
