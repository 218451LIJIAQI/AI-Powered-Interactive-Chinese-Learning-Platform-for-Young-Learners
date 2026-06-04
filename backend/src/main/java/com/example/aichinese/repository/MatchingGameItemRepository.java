package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.MatchingGameItem;

public interface MatchingGameItemRepository extends JpaRepository<MatchingGameItem, Long> {

    List<MatchingGameItem> findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(Long matchingGameId);

    @Modifying
    @Transactional
    @Query("delete from MatchingGameItem i where i.matchingGame.id = :gameId")
    int deleteByMatchingGameId(@Param("gameId") Long matchingGameId);
}
