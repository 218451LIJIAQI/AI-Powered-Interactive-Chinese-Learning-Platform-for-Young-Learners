package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import com.example.aichinese.entity.base.PracticeActivityContent;

/** The repository shape shared by the seven practice-activity types. */
@NoRepositoryBean
public interface PracticeActivityRepository<T extends PracticeActivityContent>
        extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
}
