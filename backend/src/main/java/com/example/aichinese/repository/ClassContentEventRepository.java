package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.ClassContentEvent;

public interface ClassContentEventRepository extends JpaRepository<ClassContentEvent, Long> {

    @EntityGraph(attributePaths = { "actor", "lesson" })
    List<ClassContentEvent> findByCoursePackageIdOrderByCreatedAtDescIdDesc(Long coursePackageId);

    @EntityGraph(attributePaths = { "actor", "lesson" })
    List<ClassContentEvent> findByLessonIdOrderByCreatedAtDescIdDesc(Long lessonId);

    @Modifying
    @Query("delete from ClassContentEvent e where e.coursePackage.id = :classId")
    int deleteByClassId(@Param("classId") Long classId);
}
