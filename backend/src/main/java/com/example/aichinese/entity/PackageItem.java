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
 *
 * <p>All domain fields are mapped {@code updatable = false}: a
 * package-item row identifies the {@code (parent, child)} link plus
 * the snapshot taken at bundling time, and the admin workflow removes
 * and re-adds rather than re-targeting in place. {@link #titleSnapshot}
 * in particular MUST stay frozen — its only purpose is to keep
 * customer receipts and historic catalogues bit-for-bit reproducible
 * after the underlying lesson or module is renamed.</p>
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
    @JoinColumn(name = "course_package_id", nullable = false, updatable = false)
    private CoursePackage coursePackage;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, updatable = false, length = 50)
    private PackageItemType itemType;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private Integer sortOrder = 0;

    @Column(name = "title_snapshot", updatable = false, length = 200)
    private String titleSnapshot;
}
