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
@Table(name = "drag_drop_items", indexes = {
        @Index(name = "idx_drag_drop_items_activity_id", columnList = "drag_drop_activity_id")
})
public class DragDropItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drag_drop_activity_id", nullable = false, updatable = false)
    private DragDropActivity dragDropActivity;

    @Column(name = "drag_text", nullable = false, length = 500)
    private String dragText;

    @Column(name = "target_zone", nullable = false, length = 200)
    private String targetZone;

    @Column(name = "pinyin", length = 255)
    private String pinyin;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
