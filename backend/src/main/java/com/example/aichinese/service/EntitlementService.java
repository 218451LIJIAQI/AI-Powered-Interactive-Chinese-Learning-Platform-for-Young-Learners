package com.example.aichinese.service;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.example.aichinese.entity.enums.PurchasableItemType;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.PaymentEntitlementRepository;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

/**
 * Resolves "does this student have paid access to this content?"
 * queries.
 *
 * <h2>Entitlement sources</h2>
 * <p>An entitlement row is created when a payment completes (see
 * {@link PaymentService#completeOrder}) for every purchased
 * {@link OrderItem}. Lessons inside a purchased
 * {@link com.example.aichinese.entity.CoursePackage} or
 * {@link com.example.aichinese.entity.LearningModule} are accessed
 * indirectly — {@link #hasAccessToItem} also checks whether the
 * lesson belongs to a purchased package / module, so we don't need
 * to denormalise every lesson into its own entitlement row.</p>
 *
 * <h2>Used by</h2>
 * <p>{@link StudentLearningAccessService} for lesson / quiz access,
 * {@link LiveLearningAccessService} for live class + recorded video
 * access, and {@link CartService} / {@link CheckoutService} to refuse
 * re-purchase of already-owned items.</p>
 */
@Service
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    private final PaymentEntitlementRepository paymentEntitlementRepository;
    private final OrderItemRepository orderItemRepository;
    private final PackageItemRepository packageItemRepository;
    private final LessonRepository lessonRepository;

    public EntitlementService(
            PaymentEntitlementRepository paymentEntitlementRepository,
            OrderItemRepository orderItemRepository,
            PackageItemRepository packageItemRepository,
            LessonRepository lessonRepository) {
        this.paymentEntitlementRepository = Objects.requireNonNull(paymentEntitlementRepository, "paymentEntitlementRepository");
        this.orderItemRepository = Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToItem(Long studentId, String itemType, Long itemId) {
        return paymentEntitlementRepository.existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(studentId, itemType, itemId);
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToPurchasableItem(StudentProfile student, ResolvedPurchasableItem item) {
        Long studentId = student.getId();
        if (hasAccessToItem(studentId, item.itemType().name(), item.itemId())) {
            return true;
        }

        return switch (item.itemType()) {
            case LESSON -> hasAccessToLessonInheritedFromModule(studentId, item.itemId());
            case MODULE -> hasAccessToAnyPublishedLessonInModule(studentId, item.itemId());
            case PACKAGE -> packageItemRepository
                    .findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(item.itemId()).stream()
                    .anyMatch(pi -> hasAccessToPackageItem(studentId, pi));
            default -> false;
        };
    }

    @Transactional
    public void grantAccessAfterPayment(Order order, PaymentRecord paymentRecord) {
        StudentProfile student = order.getStudent();
        List<OrderItem> orderItems = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());

        for (OrderItem orderItem : orderItems) {
            grantItemEntitlement(student, order, paymentRecord, orderItem.getItemType(), orderItem.getItemId(), null);

            if (PurchasableItemType.PREMIUM_AI.name().equals(orderItem.getItemType())) {
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

            if (PurchasableItemType.PACKAGE.name().equals(orderItem.getItemType())) {
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
        List<PaymentEntitlement> entitlements = paymentEntitlementRepository.findByOrderId(order.getId());
        for (PaymentEntitlement entitlement : entitlements) {
            entitlement.setActive(false);
        }
        if (!entitlements.isEmpty()) {
            log.info("Revoked {} entitlement(s) for orderId={}", entitlements.size(), order.getId());
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
                        && hasAccessToItem(studentId, PurchasableItemType.MODULE.name(), lesson.getModule().getId()))
                .orElse(false);
    }

    private boolean hasAccessToAnyPublishedLessonInModule(Long studentId, Long moduleId) {
        return lessonRepository.findByModuleIdAndContentStatus(moduleId, ContentStatus.PUBLISHED).stream()
                .anyMatch(lesson -> hasAccessToItem(studentId, PurchasableItemType.LESSON.name(), lesson.getId()));
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
        log.info("Entitlement granted: studentId={}, orderId={}, type={}, itemId={}, featureKey={}",
                student.getId(), order.getId(), itemType, itemId, normalisedFeatureKey);
    }
}
