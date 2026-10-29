package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.aichinese.entity.LearningMistake;
import com.example.aichinese.entity.enums.MistakeSource;

public interface LearningMistakeRepository extends JpaRepository<LearningMistake, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from LearningMistake m where m.student.id = :studentId and m.itemRef = :itemRef")
    Optional<LearningMistake> findLockedByStudentIdAndItemRef(
            @Param("studentId") Long studentId,
            @Param("itemRef") String itemRef);

    Page<LearningMistake> findByStudentIdAndClearedAtIsNullOrderByLastWrongAtDescIdDesc(Long studentId, Pageable pageable);

    Page<LearningMistake> findByStudentIdAndMistakeSourceAndClearedAtIsNullOrderByLastWrongAtDescIdDesc(
            Long studentId, MistakeSource mistakeSource, Pageable pageable);

    long countByStudentIdAndClearedAtIsNull(Long studentId);

    long countByStudentIdAndMistakeSourceAndClearedAtIsNull(Long studentId, MistakeSource mistakeSource);

    /** The waiting mistakes a review asks, oldest first. */
    List<LearningMistake> findByStudentIdAndClearedAtIsNullOrderByLastWrongAtAscIdAsc(Long studentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from LearningMistake m where m.student.id = :studentId and m.id in :ids and m.clearedAt is null")
    List<LearningMistake> findLockedWaitingByStudentIdAndIdIn(
            @Param("studentId") Long studentId,
            @Param("ids") Collection<Long> ids);

    Optional<LearningMistake> findByIdAndStudentIdAndClearedAtIsNull(Long id, Long studentId);
}
