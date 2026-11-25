package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A unit of a class. The class holds its units through {@link PackageItem}; visibility follows its lessons. */
@Getter
@Setter
@Entity
@Table(name = "learning_modules")
public class LearningModule extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;
}
