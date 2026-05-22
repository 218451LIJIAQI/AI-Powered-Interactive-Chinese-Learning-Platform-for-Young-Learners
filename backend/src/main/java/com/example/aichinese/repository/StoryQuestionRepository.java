package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StoryQuestion;

public interface StoryQuestionRepository extends JpaRepository<StoryQuestion, Long> {

    List<StoryQuestion> findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(Long storyActivityId);

    void deleteByStoryActivityId(Long storyActivityId);
}
