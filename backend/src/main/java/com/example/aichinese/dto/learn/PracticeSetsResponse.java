package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/** The published sets of one Practice type, with whether the student has done each (every item right once). */
public record PracticeSetsResponse(PracticeType type, List<Summary> sets) {

    public PracticeSetsResponse {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }

    public record Summary(
            Long id,
            String title,
            LearningStage stage,
            Integer hskLevel,
            int itemCount,
            int xpReward,
            boolean done) {
    }
}
