package com.example.aichinese.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.VideoProgress;

import java.util.List;

public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    Optional<VideoProgress> findByStudentIdAndRecordedVideoId(Long studentId, Long recordedVideoId);

    List<VideoProgress> findByRecordedVideoIdIn(Collection<Long> recordedVideoIds);
}
