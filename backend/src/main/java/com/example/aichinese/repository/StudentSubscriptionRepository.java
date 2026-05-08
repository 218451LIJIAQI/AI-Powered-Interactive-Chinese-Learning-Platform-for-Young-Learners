package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentSubscription;

public interface StudentSubscriptionRepository extends JpaRepository<StudentSubscription, Long> {
}
