package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.MatchingGameItem;

public interface MatchingGameItemRepository extends JpaRepository<MatchingGameItem, Long> {
}
