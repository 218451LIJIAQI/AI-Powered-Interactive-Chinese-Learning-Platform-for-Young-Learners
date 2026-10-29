package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearnUnitPhrase;

public interface LearnUnitPhraseRepository extends JpaRepository<LearnUnitPhrase, Long> {

    List<LearnUnitPhrase> findByUnitIdOrderByPositionAsc(Long unitId);
}
