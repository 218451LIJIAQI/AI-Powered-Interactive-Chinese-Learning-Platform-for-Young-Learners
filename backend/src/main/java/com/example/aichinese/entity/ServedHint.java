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

/**
 * One hint step the server gave a student for an item of a Learn lesson, of the Mistakes review or of a Practice set.
 * {@code itemRef} names the run's set and the item ({@code LESSON#12#345}, {@code MISTAKES#78},
 * {@code PRACTICE#LISTENING#3#2}). The authenticated run ID identifies the playing copy. The delivery time (createdAt)
 * determines whether a Practice answer followed a hint.
 */
@Getter
@Setter
@Entity
@Table(name = "served_hints", indexes = {
        @Index(name = "idx_served_hints_student_item", columnList = "student_id,item_ref,created_at"),
        @Index(name = "idx_served_hints_student_run", columnList = "student_id,run_id,item_ref")
})
public class ServedHint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "item_ref", nullable = false, length = 120, updatable = false)
    private String itemRef;

    /** Null for players opened before run IDs were issued. */
    @Column(name = "run_id", length = 36, updatable = false,
            columnDefinition = "varchar(36) character set ascii collate ascii_bin")
    private String runId;

    @Column(name = "step", nullable = false, updatable = false)
    private Integer step;
}
