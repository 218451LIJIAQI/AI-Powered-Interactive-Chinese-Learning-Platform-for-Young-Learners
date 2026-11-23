package com.example.aichinese.entity;

import com.example.aichinese.entity.base.PublishableContent;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** A unit of a class. The class holds its units through {@link PackageItem}. */
@Entity
@Table(name = "learning_modules", indexes = {
        @Index(name = "idx_learning_modules_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_learning_modules_status_created_at", columnList = "content_status,created_at")
})
public class LearningModule extends PublishableContent {
}
