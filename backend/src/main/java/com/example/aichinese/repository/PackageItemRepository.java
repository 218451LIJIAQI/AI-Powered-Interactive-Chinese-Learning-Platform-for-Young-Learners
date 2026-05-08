package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PackageItem;

public interface PackageItemRepository extends JpaRepository<PackageItem, Long> {
}
