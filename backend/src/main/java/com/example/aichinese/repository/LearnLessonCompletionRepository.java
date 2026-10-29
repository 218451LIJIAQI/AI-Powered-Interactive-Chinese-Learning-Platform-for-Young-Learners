package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.repository.projection.IdCountProjection;

public interface LearnLessonCompletionRepository extends JpaRepository<LearnLessonCompletion, Long> {

    @Query("select c from LearnLessonCompletion c join fetch c.lesson where c.student.id = :studentId")
    List<LearnLessonCompletion> findByStudentIdWithLesson(@Param("studentId") Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LearnLessonCompletion c where c.student.id = :studentId and c.lesson.id = :lessonId")
    Optional<LearnLessonCompletion> findLockedByStudentIdAndLessonId(
            @Param("studentId") Long studentId,
            @Param("lessonId") Long lessonId);

    long countByStudentId(Long studentId);

    /** Students who finished at least one stop of each unit ("N students started"). */
    @Query("""
            select l.unit.id as id, count(distinct c.student.id) as total
            from LearnLessonCompletion c join c.lesson l
            group by l.unit.id
            """)
    List<IdCountProjection> countStudentsPerUnit();
}
