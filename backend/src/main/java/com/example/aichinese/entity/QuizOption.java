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
@Table(name = "quiz_options", indexes = {
        @Index(name = "idx_quiz_options_question_id", columnList = "question_id")
})
public class QuizOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Lob
    @Column(name = "option_text", nullable = false, columnDefinition = "LONGTEXT")
    private String optionText;

    @Column(name = "correct_option", nullable = false)
    private boolean correctOption;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
