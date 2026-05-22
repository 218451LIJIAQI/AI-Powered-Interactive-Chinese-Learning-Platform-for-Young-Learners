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
    @JoinColumn(name = "story_activity_id", nullable = false)
    private StoryActivity storyActivity;

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    /**
     * JSON array of options, e.g.
     * <pre>[{"text":"option A","correct":false},{"text":"option B","correct":true}]</pre>
     * Persisted as a single JSON document to mirror the
     * {@link ListeningExercise#getQuestionPayload()} approach and avoid
     * an extra child table for what is intentionally a small set of
     * comprehension prompts.
     */
    @Lob
    @Column(name = "options_payload", columnDefinition = "TEXT", nullable = false)
    private String optionsPayload;

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
