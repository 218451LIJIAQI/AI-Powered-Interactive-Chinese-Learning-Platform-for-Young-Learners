package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentSubscription;
import com.example.aichinese.entity.enums.PaymentStatus;

public interface StudentSubscriptionRepository extends JpaRepository<StudentSubscription, Long> {

    @EntityGraph(attributePaths = { "student", "student.user", "plan", "paymentRecord" })
    List<StudentSubscription> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "student", "student.user", "plan", "paymentRecord" })
    Optional<StudentSubscription> findFirstByStudentUserIdAndSubscriptionStatusOrderByCreatedAtDesc(
            Long userId,
            PaymentStatus subscriptionStatus);

    /**
     * Returns the subscription(s) attached to a specific payment record. Used by
     * the payment activation pipeline to flip the matching subscription row to
     * PAID without scanning every subscription in the database.
     */
    @EntityGraph(attributePaths = { "student", "student.user", "plan", "paymentRecord" })
    List<StudentSubscription> findByPaymentRecordId(Long paymentRecordId);
}
