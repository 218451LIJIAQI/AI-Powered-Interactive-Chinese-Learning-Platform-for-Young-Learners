package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.repository.projection.IdCountProjection;

public interface LearnLessonCompletionRepository extends JpaRepository<LearnLessonCompletion, Long> {

    @Query("select c.lesson.id from LearnLessonCompletion c where c.student.id = :studentId")
    List<Long> findCompletedLessonIds(@Param("studentId") Long studentId);

    /** The student's stops of the given kinds first finished since {@code since}, the latest first. */
    @Query("""
            select c from LearnLessonCompletion c join fetch c.lesson lesson
            where c.student.id = :studentId and c.firstCompletedAt >= :since and lesson.lessonKind in :kinds
            order by c.firstCompletedAt desc, lesson.id asc
            """)
    List<LearnLessonCompletion> findRecentCompletions(
            @Param("studentId") Long studentId,
            @Param("since") LocalDateTime since,
            @Param("kinds") Collection<LearnLessonKind> kinds,
            Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LearnLessonCompletion c where c.student.id = :studentId and c.lesson.id = :lessonId")
    Optional<LearnLessonCompletion> findLockedByStudentIdAndLessonId(
            @Param("studentId") Long studentId,
            @Param("lessonId") Long lessonId);

    long countByStudentId(Long studentId);

    /** Finished stops of the given kinds (the lessons and reviews, for the lesson badges). */
    @Query("select count(c) from LearnLessonCompletion c where c.student.id = :studentId and c.lesson.lessonKind in :kinds")
    long countByStudentIdAndLessonKinds(
            @Param("studentId") Long studentId,
            @Param("kinds") Collection<LearnLessonKind> kinds);

    /** Per unit, the students who finished at least one of its stops ("N students started"). */
    @Query("""
            select l.unit.id as id, count(distinct c.student.id) as total
            from LearnLessonCompletion c join c.lesson l
            group by l.unit.id
            """)
    List<IdCountProjection> countStudentsPerUnit();

    @Query("select count(distinct c.student.id) from LearnLessonCompletion c where c.lesson.unit.id = :unitId")
    long countStudentsInUnit(@Param("unitId") Long unitId);
}
