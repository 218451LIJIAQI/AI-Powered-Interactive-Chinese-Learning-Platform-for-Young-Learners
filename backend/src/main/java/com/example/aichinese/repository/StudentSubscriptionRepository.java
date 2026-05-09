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
}
