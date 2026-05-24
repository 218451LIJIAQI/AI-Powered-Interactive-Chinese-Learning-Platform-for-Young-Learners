package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.MatchingGame;

/**
 * Persistence operations for {@link MatchingGame} \u2014 the platform's
 * pair-matching mini-game (match the Mandarin term to its meaning /
 * pinyin / illustration).
 *
 * <p>The repository deliberately exposes only the inherited
 * {@link JpaRepository} + {@link JpaSpecificationExecutor} surface:
 * both the admin authoring tool and the public student library compose
 * status / level / category / keyword predicates through a shared
 * {@link org.springframework.data.jpa.domain.Specification}, so a
 * single dynamic-filter API serves both reads without per-combination
 * derived methods.</p>
 */
public interface MatchingGameRepository
        extends JpaRepository<MatchingGame, Long>, JpaSpecificationExecutor<MatchingGame> {
}
