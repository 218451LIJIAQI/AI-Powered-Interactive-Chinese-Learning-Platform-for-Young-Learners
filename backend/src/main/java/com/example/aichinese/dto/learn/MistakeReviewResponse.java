package com.example.aichinese.dto.learn;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The questions of a Mistakes review (sure wrong first, then oldest, without answers). Each question's id is the mistake
 * id; {@code context} holds, in the same order, where it comes from and the last wrong answer (null when it was
 * skipped). Each hint request includes {@code runStartedAt} and {@code runSignature} so hints count toward this
 * review's allowance.
 */
public record MistakeReviewResponse(
        List<QuestionView> questions,
        List<ReviewContext> context,
        LocalDateTime runStartedAt,
        String runSignature) {

    /**
     * Where a review question comes from and what the student answered last time; {@code stillLearning} for a flashcard
     * marked "Still learning" in Flashcards.
     */
    public record ReviewContext(String sourceTitle, boolean fromLearn, String yourAnswer, boolean stillLearning) {
    }
}
