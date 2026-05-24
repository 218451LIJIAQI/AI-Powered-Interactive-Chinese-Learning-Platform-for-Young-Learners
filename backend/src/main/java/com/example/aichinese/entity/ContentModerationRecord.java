package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit row capturing one administrator moderation action against a
 * piece of platform content.
 *
 * <p>Pointers to the affected content are stored polymorphically via
 * {@link #targetType} ({@link com.example.aichinese.entity.enums.ContentModerationTargetType})
 * and {@link #targetId} so the same table tracks decisions on lessons,
 * packages, teacher profiles, etc. {@link #targetTitleSnapshot}
 * preserves the readable name at the time of the action so historic
 * audit trails remain interpretable even after rename or deletion.</p>
 *
 * <p>{@link #actionTaken} captures the verb (HIDE, UNPUBLISH, ARCHIVE,
 * REQUEST_REVISION, RESTORE) — see
 * {@link com.example.aichinese.entity.enums.ContentModerationAction}
 * — as a free-form string so future actions can be added without a
 * Flyway migration. The application layer parses through
 * {@code ContentModerationAction#tryFrom}.</p>
 *
 * <p>{@link #relatedTeacher} attributes the affected teacher (when the
 * action targets teacher-owned content), enabling per-teacher
 * compliance dashboards.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "content_moderation_records", indexes = {
        @Index(name = "idx_content_moderation_records_admin_user_id", columnList = "admin_user_id"),
        @Index(name = "idx_content_moderation_records_target_type_target_id", columnList = "target_type,target_id")
})
public class ContentModerationRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id")
    private User adminUser;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "action_taken", nullable = false, length = 100)
    private String actionTaken;

    @Lob
    @Column(name = "note", columnDefinition = "LONGTEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_teacher_id")
    private TeacherProfile relatedTeacher;

    @Column(name = "target_title_snapshot", length = 255)
    private String targetTitleSnapshot;
}
