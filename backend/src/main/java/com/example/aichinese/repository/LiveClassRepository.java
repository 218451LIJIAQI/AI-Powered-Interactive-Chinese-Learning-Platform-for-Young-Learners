package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.LiveClass;

import jakarta.persistence.LockModeType;

public interface LiveClassRepository extends JpaRepository<LiveClass, Long> {

    @Override
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @NonNull
    Optional<LiveClass> findById(@NonNull Long id);

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Optional<LiveClass> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    /**
     * A batch of classes with everything ContentVisibilityPolicy.isLiveClassContentVisible reads (lesson, module,
     * course and their teachers' accounts), in one query. Callers pass a non-empty set.
     */
    @EntityGraph(attributePaths = {
            "lesson", "lesson.module", "lesson.teacher", "lesson.teacher.user",
            "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user"
    })
    List<LiveClass> findByIdIn(Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Optional<LiveClass> findLockedById(Long id);

    /** Sessions of the given classes, earliest first. */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "coursePackage" })
    List<LiveClass> findByCoursePackageIdInOrderByScheduledStartAtAscIdAsc(Collection<Long> coursePackageIds);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "coursePackage" })
    List<LiveClass> findByTeacherIdOrderByScheduledStartAtAscIdAsc(Long teacherId);
}
