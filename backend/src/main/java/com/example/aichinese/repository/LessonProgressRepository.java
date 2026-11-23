package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    /** Opened lessons still published, newest progress first. A lesson id narrows the lookup to that lesson. */
    @Query("""
            select l from LessonProgress p join p.lesson l
            where p.student.user.id = :userId and (:lessonId is null or l.id = :lessonId)
              and l.contentStatus = com.example.aichinese.entity.enums.ContentStatus.PUBLISHED and l.revisionOf is null
            order by p.updatedAt desc, p.id desc
            """)
    List<Lesson> findOpenedPublishedLessons(
            @Param("userId") Long userId, @Param("lessonId") Long lessonId, Pageable pageable);
}
