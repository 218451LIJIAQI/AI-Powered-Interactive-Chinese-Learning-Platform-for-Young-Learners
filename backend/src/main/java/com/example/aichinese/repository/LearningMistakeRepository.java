package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from LearningMistake m where m.student.id = :studentId and m.itemRef in :itemRefs order by m.id asc")
    List<LearningMistake> findLockedByStudentIdAndItemRefIn(
            @Param("studentId") Long studentId,
            @Param("itemRefs") Collection<String> itemRefs);

    /** Waiting mistakes, the ones answered wrong while "Sure" first, then the newest. */
    List<LearningMistake> findByStudentIdAndClearedAtIsNullOrderBySureWrongDescLastWrongAtDescIdDesc(
            Long studentId, Pageable pageable);

    List<LearningMistake> findByStudentIdAndMistakeSourceAndClearedAtIsNullOrderBySureWrongDescLastWrongAtDescIdDesc(
            Long studentId, MistakeSource mistakeSource, Pageable pageable);

    long countByStudentIdAndClearedAtIsNull(Long studentId);

    long countByStudentUserIdAndClearedAtIsNull(Long userId);

    interface SourceCount {
        MistakeSource getSource();

        long getTotal();
    }

    @Query("""
            select m.mistakeSource as source, count(m) as total from LearningMistake m
            where m.student.id = :studentId and m.clearedAt is null
            group by m.mistakeSource
            """)
    List<SourceCount> countWaitingBySource(@Param("studentId") Long studentId);

    /** The waiting mistakes a review asks: the ones answered wrong while "Sure" first, then the oldest. */
    List<LearningMistake> findByStudentIdAndClearedAtIsNullOrderBySureWrongDescLastWrongAtAscIdAsc(
            Long studentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from LearningMistake m where m.student.id = :studentId and m.id in :ids and m.clearedAt is null order by m.id asc")
    List<LearningMistake> findLockedWaitingByStudentIdAndIdIn(
            @Param("studentId") Long studentId,
            @Param("ids") Collection<Long> ids);

    Optional<LearningMistake> findByIdAndStudentIdAndClearedAtIsNull(Long id, Long studentId);

    /** A learner's user id and how many of their mistakes wait. */
    record Waiting(Long userId, long waiting) {
    }

    /** Active learners with at least {@code minimum} mistakes waiting (the weekly "Mistakes to review"). */
    @Query("""
            select new com.example.aichinese.repository.LearningMistakeRepository$Waiting(
                m.student.user.id, count(m))
            from LearningMistake m
            where m.clearedAt is null and m.student.user.active = true and m.student.user.deletedAt is null
            group by m.student.user.id
            having count(m) >= :minimum
            """)
    List<Waiting> findLearnersWithWaitingMistakes(@Param("minimum") long minimum);
}
