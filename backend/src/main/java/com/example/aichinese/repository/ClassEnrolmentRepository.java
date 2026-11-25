package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;

public interface ClassEnrolmentRepository extends JpaRepository<ClassEnrolment, Long> {

    /** My classes includes published classes and the archives their learners can still open. */
    String VISIBLE_TO_MEMBER = """
            where e.student.id = :studentId and (p.contentStatus = PUBLISHED
                or (p.contentStatus = ARCHIVED and p.statusBeforeArchive = PUBLISHED))
            """;
    String VISIBLE_CLASSES = """
            select p from ClassEnrolment e join e.coursePackage p left join fetch p.category
            """ + VISIBLE_TO_MEMBER;

    boolean existsByStudentIdAndCoursePackageId(Long studentId, Long coursePackageId);

    Optional<ClassEnrolment> findByStudentIdAndCoursePackageId(Long studentId, Long coursePackageId);

    @Query(VISIBLE_CLASSES + "order by e.joinedAt desc, e.id desc")
    List<CoursePackage> findVisibleClasses(@Param("studentId") Long studentId);

    @Query(VISIBLE_CLASSES + "and p.id in :classIds")
    List<CoursePackage> findVisibleClasses(@Param("studentId") Long studentId, @Param("classIds") Collection<Long> classIds);

    @Query("select count(e) from ClassEnrolment e join e.coursePackage p " + VISIBLE_TO_MEMBER)
    long countVisibleClasses(@Param("studentId") Long studentId);

    @Query("select e.coursePackage.id from ClassEnrolment e where e.student.id = :studentId")
    List<Long> findClassIdsByStudentId(@Param("studentId") Long studentId);

    @Query("select e.coursePackage.id from ClassEnrolment e where e.student.id = :studentId and e.coursePackage.id in :classIds")
    Set<Long> findJoinedClassIds(@Param("studentId") Long studentId, @Param("classIds") Collection<Long> classIds);

    boolean existsByCoursePackageId(Long coursePackageId);

    /** Bounded ascending slice of active learner user ids who joined the class ("Lesson updated"). */
    @Query("""
            select e.student.user.id from ClassEnrolment e
            where e.coursePackage.id = :classId
              and e.student.user.active = true
              and e.student.user.deletedAt is null
              and e.student.user.id > :afterUserId
            order by e.student.user.id asc
            """)
    List<Long> findActiveLearnerIdsOfClassAfter(
            @Param("classId") Long classId,
            @Param("afterUserId") Long afterUserId,
            Pageable pageable);
}
