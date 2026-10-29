package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.enums.PublishStatus;

public interface LevelCheckQuestionRepository extends JpaRepository<LevelCheckQuestion, Long> {

    List<LevelCheckQuestion> findByPublishStatusOrderByIdAsc(PublishStatus publishStatus);

    /** Admin list: Beginner 1 before Beginner 2, easy before hard, then oldest first. */
    @Query("select q from LevelCheckQuestion q order by q.mandarinLevel asc, q.difficulty asc, q.id asc")
    Page<LevelCheckQuestion> findAllForAdmin(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from LevelCheckQuestion q where q.id = :id")
    Optional<LevelCheckQuestion> findLockedById(@Param("id") Long id);
}
