package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PaymentEntitlement;

public interface PaymentEntitlementRepository extends JpaRepository<PaymentEntitlement, Long> {

    boolean existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(Long studentId, String itemType, Long itemId);

    boolean existsByStudentIdAndFeatureKeyAndActiveTrue(Long studentId, String featureKey);

    List<PaymentEntitlement> findByOrderId(Long orderId);

    Optional<PaymentEntitlement> findByStudentIdAndItemTypeAndItemIdAndSourceTypeAndActiveTrue(
            Long studentId,
            String itemType,
            Long itemId,
            String sourceType);
}
