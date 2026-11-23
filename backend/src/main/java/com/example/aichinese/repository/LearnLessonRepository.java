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

    /**
     * The overview only needs counts, not every lesson and its unit loaded as entities: per unit, its stops without a
     * level checkpoint ({@code total}) and how many of those are bonus chests ({@code bonus}).
     */
    @Query("""
            select l.unit.id as id,
                   count(l) as total,
                   sum(case when l.lessonKind = com.example.aichinese.entity.enums.LearnLessonKind.BONUS
                            then 1 else 0 end) as bonus
            from LearnLesson l
            where l.lessonKind <> com.example.aichinese.entity.enums.LearnLessonKind.CHECKPOINT
            group by l.unit.id
            """)
    List<UnitLessonCounts> countLessonsPerUnit();

    /** One unit's row of {@link #countLessonsPerUnit()}. */
    interface UnitLessonCounts {

        Long getId();

        long getTotal();

        long getBonus();
    }
}
