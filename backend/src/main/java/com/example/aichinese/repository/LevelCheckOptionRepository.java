package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.LevelCheckOption;

public interface LevelCheckOptionRepository extends JpaRepository<LevelCheckOption, Long> {

    List<LevelCheckOption> findByQuestionIdOrderByPositionAsc(Long questionId);

    @Query("select o from LevelCheckOption o where o.question.id in :questionIds order by o.question.id asc, o.position asc")
    List<LevelCheckOption> findByQuestionIds(@Param("questionIds") Collection<Long> questionIds);

    @Modifying
    @Transactional
    @Query("delete from LevelCheckOption o where o.question.id = :questionId")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
