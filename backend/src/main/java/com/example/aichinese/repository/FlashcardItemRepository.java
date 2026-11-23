package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.FlashcardItem;

public interface FlashcardItemRepository extends JpaRepository<FlashcardItem, Long> {

    List<FlashcardItem> findByFlashcardIdOrderBySortOrderAscIdAsc(Long flashcardId);

    List<FlashcardItem> findByFlashcardIdInOrderBySortOrderAscIdAsc(Collection<Long> flashcardIds);
}
