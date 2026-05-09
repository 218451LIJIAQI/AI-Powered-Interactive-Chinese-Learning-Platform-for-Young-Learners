package com.example.aichinese.dto.learning;

public record FlashcardOptionResponse(
        Long id,
        String chineseText,
        String pinyin,
        String englishMeaning,
        String malayMeaning,
        String imageUrl,
        String audioUrl,
        Integer displayOrder) {
}
