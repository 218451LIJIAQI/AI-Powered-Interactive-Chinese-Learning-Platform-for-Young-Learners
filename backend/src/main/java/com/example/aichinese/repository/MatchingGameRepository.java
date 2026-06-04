package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.MatchingGame;

public interface MatchingGameRepository
        extends JpaRepository<MatchingGame, Long>, JpaSpecificationExecutor<MatchingGame> {
}
