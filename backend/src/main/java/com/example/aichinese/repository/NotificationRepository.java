package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
