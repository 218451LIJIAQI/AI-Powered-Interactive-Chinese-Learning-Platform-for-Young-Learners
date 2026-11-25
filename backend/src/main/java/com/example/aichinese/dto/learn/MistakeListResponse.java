package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.MistakeSource;

/**
 * One page of waiting mistakes (the ones answered wrong while "Sure" first, then the newest) and how many wait in
 * total, from Learn and from Practice.
 */
public record MistakeListResponse(
        PageResponse<Row> page,
        long all,
        long learn,
        long practice) {

    /**
     * A waiting mistake: what was asked ({@code type} or {@code prompt}, with its hanzi or meaning), what the student
     * answered (null when skipped, or for a flashcard marked "Still learning", {@code stillLearning}) and the right
     * answer.
     */
    public record Row(
            Long id,
            MistakeSource source,
            LearnQuestionType type,
            String prompt,
            String hanzi,
            String meaning,
            String yourAnswer,
            String correctAnswer,
            boolean sureWrong,
            boolean stillLearning) {

        @Override
        public String toString() {
            return "Row[id=" + id
                    + ", source=" + source
                    + ", type=" + type
                    + ", yourAnswer=" + redactLength(yourAnswer)
                    + ", sureWrong=" + sureWrong
                    + ", stillLearning=" + stillLearning + "]";
        }
    }
}
