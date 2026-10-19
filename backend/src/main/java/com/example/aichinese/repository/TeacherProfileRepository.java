package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;

public interface TeacherProfileRepository
        extends JpaRepository<TeacherProfile, Long>, JpaSpecificationExecutor<TeacherProfile> {

    Optional<TeacherProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findDetailedByUserId(Long userId);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findDetailedById(Long id);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findByIdAndTeacherStatusAndPublicVisibleTrue(Long id, TeacherStatus teacherStatus);

    @EntityGraph(attributePaths = { "user" })
    Page<TeacherProfile> findByTeacherStatusOrderByCreatedAtDesc(TeacherStatus teacherStatus, Pageable pageable);

    @EntityGraph(attributePaths = { "user" })
    Page<TeacherProfile> findAll(Specification<TeacherProfile> specification, Pageable pageable);

    @EntityGraph(attributePaths = { "user" })
    List<TeacherProfile> findByUserIdIn(Collection<Long> userIds);

    long countByTeacherStatus(TeacherStatus teacherStatus);
}
