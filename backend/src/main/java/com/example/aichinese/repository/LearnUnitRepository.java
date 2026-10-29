package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.enums.PublishStatus;

public interface LearnUnitRepository extends JpaRepository<LearnUnit, Long> {

    List<LearnUnit> findAllByOrderByPositionAsc();

    List<LearnUnit> findByPublishStatusOrderByPositionAsc(PublishStatus publishStatus);
}
