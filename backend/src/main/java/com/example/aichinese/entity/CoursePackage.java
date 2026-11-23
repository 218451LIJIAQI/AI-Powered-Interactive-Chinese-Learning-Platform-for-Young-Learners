package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.PublishableContent;
import com.example.aichinese.entity.enums.ContentStatus;

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

/** A class: a themed course the MandarinSpark team authors in the admin portal (units hold its lessons). */
@Getter
@Setter
@Entity
@Table(name = "course_packages", indexes = {
        @Index(name = "idx_course_packages_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_course_packages_status_created_at", columnList = "content_status,created_at")
})
public class CoursePackage extends PublishableContent {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private LearningCategory category;

    @Column(name = "description", length = 2000)
    private String description;

    /** The Learn unit (1 to 8) the class works best after: a label for learners, never a lock. Null when not set. */
    @Column(name = "best_after_unit")
    private Integer bestAfterUnit;

    /** Status before the class was archived, so a restore returns it there; null while not archived. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_archive", length = 30)
    private ContentStatus statusBeforeArchive;

    /** When the class was archived; null while not archived. */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
