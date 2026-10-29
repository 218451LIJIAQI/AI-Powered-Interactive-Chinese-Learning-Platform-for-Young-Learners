package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.aichinese.entity.LearnLesson;

public interface LearnLessonRepository extends JpaRepository<LearnLesson, Long> {

    /** The whole path in order (unit, then stop), with each stop's unit. */
    @Query("select l from LearnLesson l join fetch l.unit u order by u.position asc, l.position asc")
    List<LearnLesson> findPathInOrder();

    List<LearnLesson> findByUnitIdOrderByPositionAsc(Long unitId);
}
