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

    /**
     * Opened lessons that are still published and belong to a class the learner currently joined (published, or
     * archived after publication so joined learners retain read access).
     */
    String VISIBLE_OPENED_LESSON_WHERE = """
            where p.student.user.id = :userId
              and l.contentStatus = com.example.aichinese.entity.enums.ContentStatus.PUBLISHED
              and l.revisionOf is null
              and exists (
                  select 1 from PackageItem item
                  join ClassEnrolment e on e.coursePackage = item.coursePackage
                  where item.module = l.module
                    and e.student = p.student
                    and (item.coursePackage.contentStatus = com.example.aichinese.entity.enums.ContentStatus.PUBLISHED
                         or (item.coursePackage.contentStatus = com.example.aichinese.entity.enums.ContentStatus.ARCHIVED
                             and item.coursePackage.statusBeforeArchive = com.example.aichinese.entity.enums.ContentStatus.PUBLISHED))
              )
            """;

    @Query("select l from LessonProgress p join p.lesson l " + VISIBLE_OPENED_LESSON_WHERE
            + " order by p.updatedAt desc, p.id desc")
    List<Lesson> findOpenedPublishedLessons(@Param("userId") Long userId, Pageable pageable);

    @Query("select l from LessonProgress p join p.lesson l " + VISIBLE_OPENED_LESSON_WHERE
            + " and p.lesson.id = :lessonId")
    Optional<Lesson> findOpenedPublishedLesson(@Param("userId") Long userId, @Param("lessonId") Long lessonId);
}
