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
import lombok.Setter;

@Getter
@Setter
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

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
