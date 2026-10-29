package com.example.aichinese.entity.base;

import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Reviewable content a teacher can archive in any of the {@link ContentStatus#TEACHER_ARCHIVABLE} states:
 * courses, modules, lessons and quizzes. The status it had before it was archived is kept, so a restore
 * returns it there instead of publishing a draft that was never reviewed.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TeacherArchivableContent extends ReviewableContent {

    /** Status before the item was archived, so a restore returns it there; null while not archived. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_archive", length = 30)
    private ContentStatus statusBeforeArchive;
}
