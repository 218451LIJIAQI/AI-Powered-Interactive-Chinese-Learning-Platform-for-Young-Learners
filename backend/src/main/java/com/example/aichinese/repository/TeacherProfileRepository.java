package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {

    Optional<TeacherProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findDetailedByUserId(Long userId);

    @EntityGraph(attributePaths = { "user" })
    Optional<TeacherProfile> findDetailedById(Long id);


    @EntityGraph(attributePaths = { "user" })
    List<TeacherProfile> findByUserIdIn(Collection<Long> userIds);

    /** Applications waiting for a decision (sent and not decided yet), the oldest first. */
    @EntityGraph(attributePaths = { "user" })
    @Query("select p from TeacherProfile p where p.teacherStatus = com.example.aichinese.entity.enums.TeacherStatus.PENDING"
            + " and p.verificationSubmittedAt is not null order by p.verificationSubmittedAt asc, p.id asc")
    List<TeacherProfile> findInReview(Pageable pageable);

    @Query("select count(p) from TeacherProfile p where p.teacherStatus = com.example.aichinese.entity.enums.TeacherStatus.PENDING"
            + " and p.verificationSubmittedAt is not null")
    long countInReview();
}
