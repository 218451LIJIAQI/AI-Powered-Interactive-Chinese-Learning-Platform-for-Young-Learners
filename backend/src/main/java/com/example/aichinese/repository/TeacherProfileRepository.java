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
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link TeacherProfile} — the teacher-side
 * profile one-to-one with a {@link com.example.aichinese.entity.User},
 * carrying verification status, bio, public-listing flag and
 * teaching credentials.
 *
 * <p>The pessimistic-write {@link #findLockedByUserId(Long)} variant
 * underpins teacher verification status transitions
 * ({@code PENDING → APPROVED / REJECTED}): two admins approving the
 * same teacher concurrently would otherwise race the
 * {@code teacher_status} flip plus the downstream entitlement updates.</p>
 *
 * <p>{@link #findByIdAndTeacherStatusAndPublicVisibleTrue(Long, TeacherStatus)}
 * pushes the discovery filter (must be APPROVED + opted-in to the
 * public directory) down to SQL so the public profile page never leaks
 * unapproved or hidden teachers.</p>
 */
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

    /**
     * Paged status-filtered teacher list used by the admin verification
     * queue (e.g. {@code TeacherStatus.PENDING}).
     */
    @EntityGraph(attributePaths = { "user" })
    Page<TeacherProfile> findByTeacherStatusOrderByCreatedAtDesc(TeacherStatus teacherStatus, Pageable pageable);

    /**
     * Specification-based admin teacher search; eager-fetches
     * {@code user} so the response mapper renders the contact details
     * without per-row lazy loads.
     */
    @EntityGraph(attributePaths = { "user" })
    Page<TeacherProfile> findAll(Specification<TeacherProfile> specification, Pageable pageable);

    /**
     * Bulk-hydrate teacher profiles by owning user id, used by the
     * teacher-aware response mappers when assembling reports across
     * multiple authors in a single round trip.
     */
    @EntityGraph(attributePaths = { "user" })
    List<TeacherProfile> findByUserIdIn(Collection<Long> userIds);

    /**
     * Backs the admin dashboard's verification-status counter tiles
     * (e.g. "Pending teacher verifications").
     */
    long countByTeacherStatus(TeacherStatus teacherStatus);
}
