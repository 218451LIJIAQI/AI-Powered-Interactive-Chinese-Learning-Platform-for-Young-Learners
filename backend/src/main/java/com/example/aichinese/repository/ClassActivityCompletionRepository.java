package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.ClassActivityCompletion;
import com.example.aichinese.entity.enums.ClassActivityType;

public interface ClassActivityCompletionRepository extends JpaRepository<ClassActivityCompletion, Long> {

    Optional<ClassActivityCompletion> findByStudentIdAndActivityTypeAndActivityId(
            Long studentId,
            ClassActivityType activityType,
            Long activityId);

    List<ClassActivityCompletion> findByStudentIdAndLessonIdIn(Long studentId, Collection<Long> lessonIds);

    List<ClassActivityCompletion> findByStudentIdInAndLessonIdIn(Collection<Long> studentIds, Collection<Long> lessonIds);
}
