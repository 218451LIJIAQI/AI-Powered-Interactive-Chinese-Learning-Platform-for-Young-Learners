package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A unit of a class: links a {@link CoursePackage} to one of its {@link LearningModule} units by sort order. */
@Getter
@Setter
@Entity
@Table(name = "package_items", indexes = {
        @Index(name = "idx_package_items_item_package", columnList = "item_id,course_package_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_package_items_package_item", columnNames = { "course_package_id", "item_id" })
})
public class PackageItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_package_id", nullable = false, updatable = false)
    private CoursePackage coursePackage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_package_items_module"))
    private LearningModule module;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
