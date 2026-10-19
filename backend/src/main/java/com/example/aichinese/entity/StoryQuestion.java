package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "story_questions", indexes = {
        @Index(name = "idx_story_questions_activity_id", columnList = "story_activity_id")
})
public class StoryQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_activity_id", nullable = false, updatable = false)
    private StoryActivity storyActivity;

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    @Lob
    @Column(name = "options_payload", columnDefinition = "TEXT", nullable = false)
    private String optionsPayload;

    /** 0-based index of the correct option in {@code optionsPayload}; never sent to students before they answer. */
    @Column(name = "correct_option_index")
    private Integer correctOptionIndex;

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
