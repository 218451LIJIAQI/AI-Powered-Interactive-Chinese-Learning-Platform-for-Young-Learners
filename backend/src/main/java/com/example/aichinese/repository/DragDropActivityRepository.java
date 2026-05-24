package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.DragDropActivity;

/**
 * Persistence operations for {@link DragDropActivity} \u2014 the
 * platform's drag-and-drop matching mini-game (drag the pinyin onto the
 * matching character / image).
 *
 * <p>The repository deliberately exposes only the inherited
 * {@link JpaRepository} + {@link JpaSpecificationExecutor} surface:
 * the admin activity management service composes status / level /
 * category / keyword predicates as a {@link
 * org.springframework.data.jpa.domain.Specification} (see
 * {@code AdminActivityService.filterSpec}) and the public student
 * library uses the same specification for its activity feed, so a
 * single dynamic-filter API serves both read paths without a bespoke
 * derived method per filter combination.</p>
 */
public interface DragDropActivityRepository
        extends JpaRepository<DragDropActivity, Long>, JpaSpecificationExecutor<DragDropActivity> {
}
