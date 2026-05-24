package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.Flashcard;

/**
 * Persistence operations for {@link Flashcard} \u2014 the platform's
 * single-page vocabulary card (one Mandarin term + pinyin + meaning +
 * optional illustration).
 *
 * <p>The repository deliberately exposes only the inherited
 * {@link JpaRepository} + {@link JpaSpecificationExecutor} surface:
 * both the admin authoring tool and the public student library compose
 * status / level / category / keyword predicates through a shared
 * {@link org.springframework.data.jpa.domain.Specification} (see
 * {@code AdminActivityService.filterSpec} and
 * {@code StudentActivityLibraryService.filterSpec}), so a single dynamic
 * filter API serves both reads without per-combination derived methods.</p>
 */
public interface FlashcardRepository
        extends JpaRepository<Flashcard, Long>, JpaSpecificationExecutor<Flashcard> {
}
