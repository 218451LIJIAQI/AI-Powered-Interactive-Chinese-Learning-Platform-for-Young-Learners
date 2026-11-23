package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PracticeAnswer;
import com.example.aichinese.entity.enums.PracticeType;

public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, Long> {

    interface InsightCounts {
        PracticeType getPracticeType();

        long getSureAndRight();

        long getLuckyGuesses();

        long getSureButWrong();

        long getWrongGuesses();

        long getHintsUsed();
    }

    /** The week's answer counts per type, with the type that used the most hints first. */
    @Query("""
            select a.practiceType as practiceType,
                   sum(case when a.correct = true and a.confidence = com.example.aichinese.entity.enums.AnswerConfidence.SURE
                            then 1 else 0 end) as sureAndRight,
                   sum(case when a.correct = true and a.confidence = com.example.aichinese.entity.enums.AnswerConfidence.GUESS
                            then 1 else 0 end) as luckyGuesses,
                   sum(case when a.correct = false and a.confidence = com.example.aichinese.entity.enums.AnswerConfidence.SURE
                            then 1 else 0 end) as sureButWrong,
                   sum(case when a.correct = false and a.confidence = com.example.aichinese.entity.enums.AnswerConfidence.GUESS
                            then 1 else 0 end) as wrongGuesses,
                   sum(case when a.hintSteps > 0 then 1 else 0 end) as hintsUsed
            from PracticeAnswer a
            where a.student.id = :studentId and a.createdAt >= :from
            group by a.practiceType
            order by hintsUsed desc, a.practiceType asc
            """)
    List<InsightCounts> countInsightsSince(@Param("studentId") Long studentId, @Param("from") LocalDateTime from);

    /** When the student last had an answer to the item ({@code LISTENING#3#2}) checked; null if never. */
    @Query("select max(a.createdAt) from PracticeAnswer a where a.student.id = :studentId and a.itemRef = :itemRef")
    LocalDateTime findLastAnsweredAt(@Param("studentId") Long studentId, @Param("itemRef") String itemRef);
}
