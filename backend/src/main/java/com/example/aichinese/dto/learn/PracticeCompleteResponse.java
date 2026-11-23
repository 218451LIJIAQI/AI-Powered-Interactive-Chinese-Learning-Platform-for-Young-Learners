package com.example.aichinese.dto.learn;

import com.example.aichinese.entity.enums.PracticeType;

/**
 * A finished run: how many items were right (flashcards: rated "Got it"), whether every item was right (the set's XP
 * is paid the first time that happens; a flashcard set's the first time it is reviewed), the XP earned and the set of
 * the same type to offer next: one the student has not done yet when there is one, null when every set is done and
 * this was the last in the list.
 */
public record PracticeCompleteResponse(
        PracticeType type,
        int correctCount,
        int itemCount,
        boolean allCorrect,
        NextSet nextSet,
        XpResult xp) {

    public record NextSet(Long id, String title) {
    }
}
