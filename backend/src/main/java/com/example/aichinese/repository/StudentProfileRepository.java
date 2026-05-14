package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long>, JpaSpecificationExecutor<StudentProfile> {

    @EntityGraph(attributePaths = { "currentLevel" })
    Optional<StudentProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = { "user", "currentLevel" })
    Page<StudentProfile> findAll(org.springframework.data.jpa.domain.Specification<StudentProfile> specification, Pageable pageable);

    @EntityGraph(attributePaths = { "user", "currentLevel" })
    List<StudentProfile> findAll(Sort sort);

    /**
     * Returns every student that has at least one piece of progress, live-class
     * enrolment, or quiz attempt linked to the given teacher user — i.e. the
     * candidate set for the teacher feedback flow. This replaces the
     * previous {@code findAll().filter(...)} pattern in
     * {@code TeacherFeedbackService.getTeacherFeedbackCandidates}, which
     * loaded every student in the database and ran three independent
     * {@code exists} probes per row (1 + 3N queries). The DB-side query
     * here is one statement and uses the existing
     * {@code idx_lesson_progress_*}, {@code idx_live_class_enrolments_*},
     * and {@code idx_quiz_attempts_*} indexes via the join columns.
     */
    @Query("""
            select distinct sp from StudentProfile sp
            join sp.user u
            where exists (
                select 1 from LessonProgress lp
                where lp.student = sp and lp.lesson.teacher.user.id = :teacherUserId
            ) or exists (
                select 1 from LiveClassEnrolment lce
                where lce.student = sp and lce.liveClass.teacher.user.id = :teacherUserId
            ) or exists (
                select 1 from QuizAttempt qa
                where qa.student = sp and qa.quiz.teacher.user.id = :teacherUserId
            )
            order by sp.id asc
            """)
    @EntityGraph(attributePaths = { "user", "currentLevel" })
    List<StudentProfile> findStudentsLinkedToTeacher(@Param("teacherUserId") Long teacherUserId);
}
