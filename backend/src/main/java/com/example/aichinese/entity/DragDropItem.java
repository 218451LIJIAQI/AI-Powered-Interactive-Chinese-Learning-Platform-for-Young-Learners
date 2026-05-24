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

/**
 * Single draggable card belonging to a {@link DragDropActivity}.
 *
 * <p>Each row pairs a draggable label ({@link #dragText}) with the
 * target zone label it should land in ({@link #targetZone}); multiple
 * items may share the same target zone when the front-end UX presents
 * a multi-slot drop area.</p>
 *
 * <p>{@link #sortOrder} controls the initial render order so authors
 * can stage the activity (e.g. show the easier targets first); the
 * front-end is free to randomise the deck on each play, but the
 * authoritative order is preserved here for the catalogue preview.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "drag_drop_items", indexes = {
        @Index(name = "idx_drag_drop_items_activity_id", columnList = "drag_drop_activity_id")
})
public class DragDropItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drag_drop_activity_id", nullable = false)
    private DragDropActivity dragDropActivity;

    /**
     * The text the learner drags (e.g. a Chinese word "苹果"). Required.
     */
    @Column(name = "drag_text", nullable = false, length = 500)
    private String dragText;

    /**
     * The label of the drop zone the dragText must end up in for a correct
     * placement (e.g. "Fruit"). Required. Several items may share the same
     * target zone label, in which case the zone visually accepts multiple
     * draggables in the front-end.
     */
    @Column(name = "target_zone", nullable = false, length = 200)
    private String targetZone;

    /**
     * Optional pinyin shown under the dragged text to support beginners.
     */
    @Column(name = "pinyin", length = 255)
    private String pinyin;

    /**
     * Optional image URL displayed inside the draggable card.
     */
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
