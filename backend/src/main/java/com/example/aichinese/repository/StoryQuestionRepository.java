package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.StoryQuestion;

public interface StoryQuestionRepository extends JpaRepository<StoryQuestion, Long> {

    List<StoryQuestion> findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(Long storyActivityId);

    /** Questions of several stories in one query; callers group them by story id (list pages). */
    List<StoryQuestion> findByStoryActivityIdInOrderBySortOrderAscCreatedAtAsc(Collection<Long> storyActivityIds);

    @Modifying
    @Transactional
    @Query("delete from StoryQuestion q where q.storyActivity.id = :storyActivityId")
    int deleteByStoryActivityId(@Param("storyActivityId") Long storyActivityId);
}
