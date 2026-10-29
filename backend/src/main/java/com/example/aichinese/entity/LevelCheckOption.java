package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;

/** One answer of a level check question, with why it is right or what it means instead. */
@Getter
@Setter
@Entity
@Table(name = "level_check_options", indexes = {
        @Index(name = "idx_level_check_options_question", columnList = "question_id,position")
})
public class LevelCheckOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private LevelCheckQuestion question;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "option_text", nullable = false, length = 200)
    private String optionText;

    @Column(name = "correct", nullable = false)
    private boolean correct;

    @Column(name = "explanation", length = 300)
    private String explanation;
}
