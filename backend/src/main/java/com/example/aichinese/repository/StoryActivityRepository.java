package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.StoryActivity;

/**
 * Persistence operations for {@link StoryActivity} \u2014 the platform's
 * interactive narrative activity (short Mandarin story plus
 * comprehension questions).
 *
 * <p>The repository deliberately exposes only the inherited
 * {@link JpaRepository} + {@link JpaSpecificationExecutor} surface so
 * both admin authoring and student library reads share the same
 * dynamic {@link org.springframework.data.jpa.domain.Specification}-
 * based filter API without per-combination derived methods.</p>
 */
public interface StoryActivityRepository
        extends JpaRepository<StoryActivity, Long>, JpaSpecificationExecutor<StoryActivity> {
}
