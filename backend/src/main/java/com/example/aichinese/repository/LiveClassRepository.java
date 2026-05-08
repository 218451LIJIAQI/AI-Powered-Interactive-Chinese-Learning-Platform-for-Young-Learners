package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LiveClass;

public interface LiveClassRepository extends JpaRepository<LiveClass, Long> {
}
