package com.example.aichinese.dto.learn;

import java.util.List;

/**
 * The questions of a Mistakes review (the oldest waiting mistakes, without answers) and how many mistakes wait in
 * total. Each question's id is the mistake id; {@code context} holds, in the same order, where it comes from and the
 * last wrong answer (null when it was skipped).
 */
public record MistakeReviewResponse(
        long waiting,
        List<QuestionView> questions,
        List<ReviewContext> context) {

    public MistakeReviewResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
        context = context == null ? List.of() : List.copyOf(context);
    }

    /** Where a review question comes from and what the student answered last time. */
    public record ReviewContext(String sourceTitle, boolean fromLearn, String yourAnswer) {
    }
}
