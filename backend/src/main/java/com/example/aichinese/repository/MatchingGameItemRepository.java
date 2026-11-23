package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.MatchingGameItem;

public interface MatchingGameItemRepository extends JpaRepository<MatchingGameItem, Long> {

    @Query("""
            select i from MatchingGameItem i where i.matchingGame.id = :gameId
            order by i.sortOrder, i.createdAt, i.id
            """)
    List<MatchingGameItem> findInDisplayOrder(@Param("gameId") Long gameId);

    @Query("""
            select i from MatchingGameItem i where i.matchingGame.id in :gameIds
            order by i.sortOrder, i.createdAt, i.id
            """)
    List<MatchingGameItem> findInDisplayOrder(@Param("gameIds") Collection<Long> gameIds);
}
