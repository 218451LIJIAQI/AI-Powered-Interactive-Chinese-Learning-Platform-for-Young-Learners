package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PlanReason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One short note Talk with Xiaomei remembers about a learner, used only in that learner's own chats (S7.14). The goal of
 * the learner's plan is a note with {@code planReason} set ({@code text} is the plan's goal title, null for a plan built
 * from rules); any other note is a fact the learner shared in a chat ({@code text} only). {@code forgottenAt} is set while
 * the learner has told Xiaomei to forget it: such a note is never shown or sent, and its Undo clears the time again.
 */
@Getter
@Setter
@Entity
@Table(name = "xiaomei_memory_notes", indexes = {
        @Index(name = "idx_xiaomei_memory_notes_student", columnList = "student_user_id,forgotten_at")
})
public class XiaomeiMemoryNote extends BaseEntity {

    /** The longest note: a short phrase, never a paragraph. */
    public static final int MAX_TEXT_LENGTH = 80;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_xiaomei_memory_notes_student_user"))
    private User studentUser;

    @Column(name = "note_text", updatable = false, length = MAX_TEXT_LENGTH)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_reason", updatable = false, length = 20)
    private PlanReason planReason;

    @Column(name = "forgotten_at")
    private LocalDateTime forgottenAt;

    /** True for the note that is the goal of the learner's plan. */
    public boolean isPlanGoal() {
        return planReason != null;
    }
}
