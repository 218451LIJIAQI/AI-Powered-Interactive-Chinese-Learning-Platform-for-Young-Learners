package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.enums.PublishStatus;

public interface LevelCheckQuestionRepository extends JpaRepository<LevelCheckQuestion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LevelCheckQuestion> findLockedById(Long id);

    List<LevelCheckQuestion> findByPublishStatusOrderByIdAsc(PublishStatus publishStatus);

    /** Includes earlier questions even if they were unpublished during the learner's run. */
    @Query("""
            select q from LevelCheckQuestion q
            where q.publishStatus = com.example.aichinese.entity.enums.PublishStatus.PUBLISHED or q.id in :answeredIds
            order by q.id asc
            """)
    List<LevelCheckQuestion> findPublishedOrAnswered(@Param("answeredIds") Set<Long> answeredIds);

    /** Admin list: Beginner 1 before Beginner 2, easy then medium then hard (not alphabetical), then oldest first. */
    @Query(value = """
            select q from LevelCheckQuestion q
            order by q.mandarinLevel asc,
                     case when q.difficulty = com.example.aichinese.entity.enums.ActivityDifficulty.EASY then 0
                          when q.difficulty = com.example.aichinese.entity.enums.ActivityDifficulty.MEDIUM then 1
                          else 2 end,
                     q.id asc
            """,
            countQuery = "select count(q) from LevelCheckQuestion q")
    Page<LevelCheckQuestion> findAllForAdmin(Pageable pageable);

    /** Counts one answer in one statement, so students answering the same question at once are all counted. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LevelCheckQuestion q set q.answeredCount = q.answeredCount + 1, "
            + "q.correctCount = q.correctCount + :correct where q.id = :id")
    void countAnswer(@Param("id") Long id, @Param("correct") int correct);
}
