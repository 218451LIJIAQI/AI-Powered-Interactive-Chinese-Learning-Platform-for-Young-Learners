package com.example.aichinese.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;

import jakarta.persistence.LockModeType;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long>, JpaSpecificationExecutor<TeacherProfile> {

    Optional<TeacherProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findDetailedByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user" })
    @Query("select tp from TeacherProfile tp where tp.user.id = :userId")
    Optional<TeacherProfile> findLockedByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findDetailedById(Long id);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findByIdAndTeacherStatusAndPublicVisibleTrue(Long id, TeacherStatus teacherStatus);

    @EntityGraph(attributePaths = { "user" })
    java.util.List<TeacherProfile> findByTeacherStatusOrderByCreatedAtDesc(TeacherStatus teacherStatus);

    @EntityGraph(attributePaths = { "user" })
    Page<TeacherProfile> findByTeacherStatusOrderByCreatedAtDesc(TeacherStatus teacherStatus, Pageable pageable);

    @EntityGraph(attributePaths = { "user" })
    Page<TeacherProfile> findAll(org.springframework.data.jpa.domain.Specification<TeacherProfile> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "user" })
    @NonNull
    java.util.List<TeacherProfile> findAll(@NonNull Sort sort);

    @EntityGraph(attributePaths = { "user" })
    java.util.List<TeacherProfile> findByUserIdIn(Collection<Long> userIds);

    long countByTeacherStatus(TeacherStatus teacherStatus);
}
