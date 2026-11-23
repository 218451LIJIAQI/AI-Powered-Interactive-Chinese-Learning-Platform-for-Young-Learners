package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.enums.PublishStatus;

public interface LearnUnitRepository extends JpaRepository<LearnUnit, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearnUnit> findLockedById(Long id);

    List<LearnUnit> findAllByOrderByPositionAsc();

    List<LearnUnit> findByPublishStatusOrderByPositionAsc(PublishStatus publishStatus);
}
