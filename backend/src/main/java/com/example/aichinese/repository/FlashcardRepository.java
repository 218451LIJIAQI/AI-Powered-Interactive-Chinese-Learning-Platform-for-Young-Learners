package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Flashcard;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
}
