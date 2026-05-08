package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "saved_items", indexes = {
        @Index(name = "idx_saved_items_student_id", columnList = "student_id"),
        @Index(name = "idx_saved_items_item_type_item_id", columnList = "item_type,item_id")
})
public class SavedItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "item_type", nullable = false, length = 50)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "note", length = 500)
    private String note;
}
