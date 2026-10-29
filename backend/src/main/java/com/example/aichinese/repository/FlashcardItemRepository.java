package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.FlashcardItem;

public interface FlashcardItemRepository extends JpaRepository<FlashcardItem, Long> {

    List<FlashcardItem> findByFlashcardIdOrderBySortOrderAscIdAsc(Long flashcardId);

    @Modifying
    @Transactional
    @Query("delete from FlashcardItem i where i.flashcard.id = :flashcardId")
    int deleteByFlashcardId(@Param("flashcardId") Long flashcardId);
}
