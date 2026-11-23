package com.example.aichinese.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.ServedHint;

public interface ServedHintRepository extends JpaRepository<ServedHint, Long> {

    /**
     * Count distinct items in a signed run, keeping the predicate on {@code (student_id, run_id, item_ref)} indexable.
     */
    @Query("""
            select count(distinct h.itemRef) >= :limit and count(case when h.itemRef = :itemRef then 1 end) = 0
            from ServedHint h
            where h.student.id = :studentId and h.runId = :runId and h.itemRef like :setRefPrefix escape '!'
            """)
    boolean allowanceUsedForRun(@Param("studentId") Long studentId, @Param("setRefPrefix") String setRefPrefix,
            @Param("itemRef") String itemRef, @Param("runId") String runId, @Param("limit") int limit);

    /** Legacy players without a run id count unassigned hints delivered since the run started. */
    @Query("""
            select count(distinct h.itemRef) >= :limit and count(case when h.itemRef = :itemRef then 1 end) = 0
            from ServedHint h
            where h.student.id = :studentId and h.runId is null and h.createdAt >= :runStart
              and h.itemRef like :setRefPrefix escape '!'
            """)
    boolean allowanceUsedForLegacyRun(@Param("studentId") Long studentId, @Param("setRefPrefix") String setRefPrefix,
            @Param("itemRef") String itemRef, @Param("runStart") LocalDateTime runStart, @Param("limit") int limit);

    boolean existsByStudentIdAndItemRefAndRunId(Long studentId, String itemRef, String runId);

    boolean existsByStudentIdAndItemRefAndRunIdAndCreatedAtAfter(
            Long studentId, String itemRef, String runId, LocalDateTime after);

    /** The highest hint step the item got after {@code after}; 0 when it got none. */
    @Query("""
            select coalesce(max(h.step), 0) from ServedHint h
            where h.student.id = :studentId and h.itemRef = :itemRef and h.createdAt > :after
            """)
    int findHighestStepAfter(@Param("studentId") Long studentId, @Param("itemRef") String itemRef,
            @Param("after") LocalDateTime after);
}
