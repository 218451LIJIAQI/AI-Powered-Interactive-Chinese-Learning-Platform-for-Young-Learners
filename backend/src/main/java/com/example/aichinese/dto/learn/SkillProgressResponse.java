package com.example.aichinese.dto.learn;

import java.util.List;

/**
 * The Skills card of the Progress page, computed from the student's real completions.
 *
 * @param skills every skill, in display order
 * @param weakest the skill with the lowest percent; null until the student has completed anything
 */
public record SkillProgressResponse(List<SkillRow> skills, Skill weakest) {

    public SkillProgressResponse {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public enum Skill {

        VOCABULARY,

        LISTENING,

        CHARACTERS,

        SENTENCES
    }

    /**
     * @param percent the share of the skill's published items the student completed
     * @param weeklyChange percentage points gained from completions in the last 7 days
     */
    public record SkillRow(Skill skill, int percent, int weeklyChange) {
    }
}
