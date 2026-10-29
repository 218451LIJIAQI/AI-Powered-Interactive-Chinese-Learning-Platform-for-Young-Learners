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

/** A key phrase in a unit's guidebook (students hear it with Listen). */
@Getter
@Setter
@Entity
@Table(name = "learn_unit_phrases", indexes = {
        @Index(name = "idx_learn_unit_phrases_unit", columnList = "unit_id,position")
})
public class LearnUnitPhrase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false, updatable = false)
    private LearnUnit unit;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "hanzi", nullable = false, length = 100)
    private String hanzi;

    @Column(name = "pinyin", nullable = false, length = 200)
    private String pinyin;

    @Column(name = "meaning", nullable = false, length = 200)
    private String meaning;
}
