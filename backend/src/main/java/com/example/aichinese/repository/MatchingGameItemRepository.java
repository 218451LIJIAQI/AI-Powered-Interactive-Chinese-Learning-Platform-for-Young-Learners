package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.MatchingGameItem;

public interface MatchingGameItemRepository extends JpaRepository<MatchingGameItem, Long> {

    List<MatchingGameItem> findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(Long matchingGameId);

    void deleteByMatchingGameId(Long matchingGameId);
}
