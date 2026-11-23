package com.example.aichinese.dto.learn;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The published sets of one Practice type, with whether the student has done each (every item right once) or has
 * started it (finished a run that was not all right yet).
 */
public record PracticeSetsResponse(List<Summary> sets) {

    @Schema(name = "PracticeSetSummary")
    public record Summary(
            Long id,
            String title,
            Integer hskLevel,
            int itemCount,
            int xpReward,
            boolean done,
            boolean started) {
    }
}
