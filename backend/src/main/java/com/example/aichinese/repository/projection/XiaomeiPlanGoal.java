package com.example.aichinese.repository.projection;

import java.util.Objects;

import com.example.aichinese.entity.XiaomeiMemoryNote;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.util.TextUtils;

/** Projection of the learner's active plan goal: SparkBot's goal title (null for a rule-built plan) and its reason. */
public record XiaomeiPlanGoal(String goalTitle, PlanReason reason) {

    public XiaomeiPlanGoal {
        goalTitle = TextUtils.trimToNull(goalTitle);
    }

    public boolean isNoteOf(XiaomeiMemoryNote note) {
        return note.isPlanGoal() && reason == note.getPlanReason() && Objects.equals(goalTitle, note.getText());
    }
}
