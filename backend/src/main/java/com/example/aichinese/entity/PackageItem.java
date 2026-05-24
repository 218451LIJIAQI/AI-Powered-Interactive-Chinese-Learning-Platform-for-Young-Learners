package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PackageItemType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single inclusion of a {@link Lesson} or {@link LearningModule}
 * inside a parent {@link CoursePackage}.
 *
 * <p>The {@code (item_type, item_id)} pair points polymorphically at
 * either a module or a lesson — packages cannot recursively contain
 * other packages, which is enforced at the
 * {@link com.example.aichinese.entity.enums.PackageItemType} level
 * (it has only {@code MODULE} and {@code LESSON} constants).</p>
 *
 * <p>{@link #titleSnapshot} freezes the included artefact's title at
 * the time of bundling so a renamed lesson does not silently rebrand
 * historic packages on customer receipts.</p>
 *
 * <p>{@link #sortOrder} is the package author's preferred ordering and
 * is honoured when the catalogue lists package contents.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "package_items", indexes = {
        @Index(name = "idx_package_items_package_id", columnList = "course_package_id")
})
public class PackageItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_package_id", nullable = false)
    private CoursePackage coursePackage;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private PackageItemType itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "title_snapshot", length = 200)
    private String titleSnapshot;
}
