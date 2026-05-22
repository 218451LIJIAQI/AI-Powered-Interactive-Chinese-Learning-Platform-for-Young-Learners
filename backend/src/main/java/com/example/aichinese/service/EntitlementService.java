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
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.PaymentEntitlementRepository;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

@Service
public class EntitlementService {

    private final PaymentEntitlementRepository paymentEntitlementRepository;
    private final OrderItemRepository orderItemRepository;
    private final PackageItemRepository packageItemRepository;
    private final LessonRepository lessonRepository;

    public EntitlementService(
            PaymentEntitlementRepository paymentEntitlementRepository,
            OrderItemRepository orderItemRepository,
            PackageItemRepository packageItemRepository,
            LessonRepository lessonRepository) {
        this.paymentEntitlementRepository = paymentEntitlementRepository;
        this.orderItemRepository = orderItemRepository;
        this.packageItemRepository = packageItemRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToItem(Long studentId, String itemType, Long itemId) {
        return paymentEntitlementRepository.existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(studentId, itemType, itemId);
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToPurchasableItem(StudentProfile student, ResolvedPurchasableItem item) {
        Long studentId = student.getId();
        String itemType = item.itemType().name();
        if (hasAccessToItem(studentId, itemType, item.itemId())) {
            return true;
        }

        if ("LESSON".equals(itemType)) {
            return hasAccessToLessonInheritedFromModule(studentId, item.itemId());
        }

        if ("MODULE".equals(itemType)) {
            return hasAccessToAnyPublishedLessonInModule(studentId, item.itemId());
        }

        if ("PACKAGE".equals(itemType)) {
            return packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(item.itemId()).stream()
                    .anyMatch(packageItem -> hasAccessToPackageItem(studentId, packageItem));
        }

        return false;
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

    private boolean hasAccessToPackageItem(Long studentId, PackageItem packageItem) {
        if (packageItem.getItemType() == null || packageItem.getItemId() == null) {
            return false;
        }
        if (hasAccessToItem(studentId, packageItem.getItemType().name(), packageItem.getItemId())) {
            return true;
        }
        if (packageItem.getItemType() == PackageItemType.MODULE) {
            return hasAccessToAnyPublishedLessonInModule(studentId, packageItem.getItemId());
        }
        if (packageItem.getItemType() == PackageItemType.LESSON) {
            return hasAccessToLessonInheritedFromModule(studentId, packageItem.getItemId());
        }
        return false;
    }

    private boolean hasAccessToLessonInheritedFromModule(Long studentId, Long lessonId) {
        return lessonRepository.findById(lessonId)
                .map(lesson -> lesson.getModule() != null
                        && hasAccessToItem(studentId, "MODULE", lesson.getModule().getId()))
                .orElse(false);
    }

    private boolean hasAccessToAnyPublishedLessonInModule(Long studentId, Long moduleId) {
        return lessonRepository.findByModuleIdAndContentStatus(moduleId, ContentStatus.PUBLISHED).stream()
                .anyMatch(lesson -> hasAccessToItem(studentId, "LESSON", lesson.getId()));
    }

    private void grantItemEntitlement(
            StudentProfile student,
            Order order,
            PaymentRecord paymentRecord,
            String itemType,
            Long itemId,
            String featureKey) {
        // Idempotency guard. PaymentService.verifyPaymentStatus has a small
        // window where two concurrent calls can both observe PENDING and both
        // call markPaymentPaid before either has committed; without this
        // check the same (order, itemType, itemId, featureKey) tuple would
        // produce duplicate entitlement rows. Once a row exists for the
        // tuple we skip the insert — refunds use revokeAccessAfterRefund
        // which deactivates every row owned by the order, so duplicate
        // suppression here does not break the refund pipeline.
        String normalisedFeatureKey = featureKey != null && featureKey.isBlank() ? null : featureKey;
        if (paymentEntitlementRepository.existsForOrderItem(order.getId(), itemType, itemId, normalisedFeatureKey)) {
            return;
        }

        PaymentEntitlement entitlement = new PaymentEntitlement();
        entitlement.setStudent(student);
        entitlement.setOrder(order);
        entitlement.setPaymentRecord(paymentRecord);
        entitlement.setItemType(itemType);
        entitlement.setItemId(itemId);
        entitlement.setFeatureKey(normalisedFeatureKey);
        entitlement.setSourceType(normalisedFeatureKey != null ? normalisedFeatureKey : itemType);
        entitlement.setActive(true);
        paymentEntitlementRepository.save(entitlement);
    }
}
