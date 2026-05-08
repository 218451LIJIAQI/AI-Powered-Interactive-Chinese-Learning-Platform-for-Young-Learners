package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByName(String name);
}
