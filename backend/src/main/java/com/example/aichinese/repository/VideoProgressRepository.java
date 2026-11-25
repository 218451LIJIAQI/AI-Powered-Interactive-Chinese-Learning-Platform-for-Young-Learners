package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.VideoProgress;

public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {

    Optional<VideoProgress> findByStudentIdAndRecordedVideoId(Long studentId, Long recordedVideoId);

    List<VideoProgress> findByStudentIdAndRecordedVideoIdIn(Long studentId, Collection<Long> recordedVideoIds);

    boolean existsByRecordedVideoId(Long recordedVideoId);
}
