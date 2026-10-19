package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.StudentProfile;

import jakarta.persistence.LockModeType;

public interface StudentProfileRepository
        extends JpaRepository<StudentProfile, Long>, JpaSpecificationExecutor<StudentProfile> {

    @EntityGraph(attributePaths = { "currentLevel" })
    Optional<StudentProfile> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user", "currentLevel" })
    @Query("select sp from StudentProfile sp where sp.user.id = :userId")
    Optional<StudentProfile> findLockedByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user", "currentLevel" })
    @Query("select sp from StudentProfile sp where sp.id = :id")
    Optional<StudentProfile> findLockedById(@Param("id") Long id);

    @EntityGraph(attributePaths = { "user", "currentLevel" })
    @NonNull
    Page<StudentProfile> findAll(@Nullable Specification<StudentProfile> specification, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = { "user", "currentLevel" })
    List<StudentProfile> findByIdIn(List<Long> ids);

    @Query(value = """
            select sp.id
            from student_profiles sp
            join users u on sp.user_id = u.id
            join (
                select linked.student_id, max(linked.activity_at) as latest_activity_at
                from (
                    select lp.student_id as student_id, coalesce(lp.last_accessed_at, lp.updated_at, lp.created_at) as activity_at
                    from lesson_progress lp
                    join lessons l on lp.lesson_id = l.id
                    where l.teacher_id = :teacherId
                    union all
                    select qa.student_id as student_id, coalesce(qa.completed_at, qa.created_at) as activity_at
                    from quiz_attempts qa
                    join quizzes q on qa.quiz_id = q.id
                    join lessons ql on q.lesson_id = ql.id
                    where q.teacher_id = :teacherId
                    union all
                    select lce.student_id as student_id, coalesce(lce.attended_at, lce.enrolled_at, lce.created_at) as activity_at
                    from live_class_enrolments lce
                    join live_classes lc on lce.live_class_id = lc.id
                    where lc.teacher_id = :teacherId
                      and lce.enrolment_status <> 'CANCELLED'
                ) linked
                group by linked.student_id
            ) latest on latest.student_id = sp.id
            order by latest.latest_activity_at desc, lower(coalesce(u.display_name, sp.display_name, '')) asc, sp.id asc
            """,
            countQuery = """
            select count(*)
            from (
                select linked.student_id
                from (
                    select lp.student_id as student_id
                    from lesson_progress lp
                    join lessons l on lp.lesson_id = l.id
                    where l.teacher_id = :teacherId
                    union
                    select qa.student_id as student_id
                    from quiz_attempts qa
                    join quizzes q on qa.quiz_id = q.id
                    join lessons ql on q.lesson_id = ql.id
                    where q.teacher_id = :teacherId
                    union
                    select lce.student_id as student_id
                    from live_class_enrolments lce
                    join live_classes lc on lce.live_class_id = lc.id
                    where lc.teacher_id = :teacherId
                      and lce.enrolment_status <> 'CANCELLED'
                ) linked
                group by linked.student_id
            ) counted
            """,
            nativeQuery = true)
    Page<Long> findLinkedStudentIdsByTeacherId(@Param("teacherId") Long teacherId, Pageable pageable);

    /** Every student on the teacher's roster (same sources as {@link #findLinkedStudentIdsByTeacherId}). */
    @Query(value = """
            select lp.student_id
            from lesson_progress lp
            join lessons l on lp.lesson_id = l.id
            where l.teacher_id = :teacherId
            union
            select qa.student_id
            from quiz_attempts qa
            join quizzes q on qa.quiz_id = q.id
            where q.teacher_id = :teacherId
            union
            select lce.student_id
            from live_class_enrolments lce
            join live_classes lc on lce.live_class_id = lc.id
            where lc.teacher_id = :teacherId
              and lce.enrolment_status <> 'CANCELLED'
            """,
            nativeQuery = true)
    List<Long> findAllLinkedStudentIdsByTeacherId(@Param("teacherId") Long teacherId);
}
