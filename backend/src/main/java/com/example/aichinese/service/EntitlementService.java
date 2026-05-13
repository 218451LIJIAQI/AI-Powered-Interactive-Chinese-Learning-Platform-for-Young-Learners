package com.example.aichinese.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentEntitlement;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.PaymentEntitlementRepository;

@Service
public class EntitlementService {

    private final PaymentEntitlementRepository paymentEntitlementRepository;
    private final OrderItemRepository orderItemRepository;
    private final PackageItemRepository packageItemRepository;

    public EntitlementService(
            PaymentEntitlementRepository paymentEntitlementRepository,
            OrderItemRepository orderItemRepository,
            PackageItemRepository packageItemRepository) {
        this.paymentEntitlementRepository = paymentEntitlementRepository;
        this.orderItemRepository = orderItemRepository;
        this.packageItemRepository = packageItemRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToItem(Long studentId, String itemType, Long itemId) {
        return paymentEntitlementRepository.existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(studentId, itemType, itemId);
    }

    @Transactional
    public void grantAccessAfterPayment(Order order, PaymentRecord paymentRecord) {
        StudentProfile student = order.getStudent();
        List<OrderItem> orderItems = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());

        for (OrderItem orderItem : orderItems) {
            grantItemEntitlement(student, order, paymentRecord, orderItem.getItemType(), orderItem.getItemId(), null);

            if ("PREMIUM_AI".equals(orderItem.getItemType())) {
                grantItemEntitlement(
                        student,
                        order,
                        paymentRecord,
                        orderItem.getItemType(),
                        orderItem.getItemId(),
                        orderItem.getFeatureKey() == null || orderItem.getFeatureKey().isBlank()
                                ? "PREMIUM_AI_ACCESS"
                                : orderItem.getFeatureKey());
            }

            if ("PACKAGE".equals(orderItem.getItemType())) {
                for (PackageItem packageItem : packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(orderItem.getItemId())) {
                    if (packageItem.getItemType() == null) {
                        continue;
                    }
                    switch (packageItem.getItemType()) {
                        case MODULE, LESSON -> grantItemEntitlement(
                                student,
                                order,
                                paymentRecord,
                                packageItem.getItemType().name(),
                                packageItem.getItemId(),
                                null);
                        default -> {
                            // Activity-level access is reserved for future expansion.
                        }
                    }
                }
            }
        }
    }

    @Transactional
    public void revokeAccessAfterRefund(Order order) {
        for (PaymentEntitlement entitlement : paymentEntitlementRepository.findByOrderId(order.getId())) {
            entitlement.setActive(false);
        }
    }

    private void grantItemEntitlement(
            StudentProfile student,
            Order order,
            PaymentRecord paymentRecord,
            String itemType,
            Long itemId,
            String featureKey) {
        PaymentEntitlement entitlement = new PaymentEntitlement();
        entitlement.setStudent(student);
        entitlement.setOrder(order);
        entitlement.setPaymentRecord(paymentRecord);
        entitlement.setItemType(itemType);
        entitlement.setItemId(itemId);
        entitlement.setFeatureKey(featureKey);
        entitlement.setSourceType(featureKey != null && !featureKey.isBlank() ? featureKey : itemType);
        entitlement.setActive(true);
        paymentEntitlementRepository.save(entitlement);
    }
}
