package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.StoryActivity;

public interface StoryActivityRepository
        extends JpaRepository<StoryActivity, Long>, JpaSpecificationExecutor<StoryActivity> {
}
