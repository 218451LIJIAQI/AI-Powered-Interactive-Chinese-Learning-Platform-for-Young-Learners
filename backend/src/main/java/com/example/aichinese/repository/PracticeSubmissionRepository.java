package com.example.aichinese.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PracticeSubmission;
import com.example.aichinese.entity.enums.PracticeReviewStatus;
import com.example.aichinese.entity.enums.PracticeSubmissionType;

public interface PracticeSubmissionRepository extends JpaRepository<PracticeSubmission, Long> {

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "lesson.teacher", "uploadedFile" })
    @Query(value = """
            select s from PracticeSubmission s
            where s.student.id = :studentId
              and (:type is null or s.submissionType = :type)
            order by s.submittedAt desc, s.id desc
            """,
            countQuery = """
            select count(s) from PracticeSubmission s
            where s.student.id = :studentId
              and (:type is null or s.submissionType = :type)
            """)
    Page<PracticeSubmission> findForStudent(
            @Param("studentId") Long studentId,
            @Param("type") PracticeSubmissionType type,
            Pageable pageable);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "lesson.teacher", "uploadedFile" })
    Optional<PracticeSubmission> findByIdAndStudentId(Long id, Long studentId);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "lesson.teacher", "uploadedFile" })
    @Query("select s from PracticeSubmission s where s.id = :id")
    Optional<PracticeSubmission> findDetailedById(@Param("id") Long id);

    /**
     * Submissions an approved teacher may review: the student is on the teacher's roster
     * ({@code studentIds}, never empty) or the submission's lesson belongs to the teacher.
     */
    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "lesson.teacher", "uploadedFile" })
    @Query(value = """
            select s from PracticeSubmission s
            left join s.lesson lesson
            where (s.student.id in :studentIds or lesson.teacher.id = :teacherId)
              and (:reviewStatus is null or s.reviewStatus = :reviewStatus)
              and (:type is null or s.submissionType = :type)
            order by s.submittedAt desc, s.id desc
            """,
            countQuery = """
            select count(s) from PracticeSubmission s
            left join s.lesson lesson
            where (s.student.id in :studentIds or lesson.teacher.id = :teacherId)
              and (:reviewStatus is null or s.reviewStatus = :reviewStatus)
              and (:type is null or s.submissionType = :type)
            """)
    Page<PracticeSubmission> findReviewableByTeacher(
            @Param("teacherId") Long teacherId,
            @Param("studentIds") Collection<Long> studentIds,
            @Param("reviewStatus") PracticeReviewStatus reviewStatus,
            @Param("type") PracticeSubmissionType type,
            Pageable pageable);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "lesson.teacher", "uploadedFile" })
    @Query(value = """
            select s from PracticeSubmission s
            where (:reviewStatus is null or s.reviewStatus = :reviewStatus)
              and (:type is null or s.submissionType = :type)
              and (:studentId is null or s.student.id = :studentId)
            order by s.submittedAt desc, s.id desc
            """,
            countQuery = """
            select count(s) from PracticeSubmission s
            where (:reviewStatus is null or s.reviewStatus = :reviewStatus)
              and (:type is null or s.submissionType = :type)
              and (:studentId is null or s.student.id = :studentId)
            """)
    Page<PracticeSubmission> findForAdmin(
            @Param("reviewStatus") PracticeReviewStatus reviewStatus,
            @Param("type") PracticeSubmissionType type,
            @Param("studentId") Long studentId,
            Pageable pageable);
}
