package com.example.aichinese.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct u from User u left join fetch u.roles where lower(u.email) = lower(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findLockedByEmail(@Param("email") String email);

    @Query("select count(distinct u) from User u join u.roles r where r.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    java.util.List<User> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "roles" })
    Page<User> findAll(org.springframework.data.jpa.domain.Specification<User> specification, Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    java.util.List<User> findByIdIn(Collection<Long> ids);

    @Query(value = """
            select u.id
            from users u
            where (:roleName is null or exists (
                  select 1 from user_roles ur
                  join roles r on r.id = ur.role_id
                  where ur.user_id = u.id
                    and r.name = :roleName
              ))
              and (:keyword is null or lower(u.display_name) like :keyword or lower(u.email) like :keyword)
              and (:teacherStatus is null or exists (
                  select 1 from teacher_profiles tp
                  where tp.user_id = u.id
                    and tp.teacher_status = :teacherStatus
              ))
            order by u.created_at desc, u.id desc
            """,
            countQuery = """
            select count(*)
            from users u
            where (:roleName is null or exists (
                  select 1 from user_roles ur
                  join roles r on r.id = ur.role_id
                  where ur.user_id = u.id
                    and r.name = :roleName
              ))
              and (:keyword is null or lower(u.display_name) like :keyword or lower(u.email) like :keyword)
              and (:teacherStatus is null or exists (
                  select 1 from teacher_profiles tp
                  where tp.user_id = u.id
                    and tp.teacher_status = :teacherStatus
              ))
            """,
            nativeQuery = true)
    Page<Long> findAdminUserIds(
            @Param("roleName") String roleName,
            @Param("keyword") String keyword,
            @Param("teacherStatus") String teacherStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    @Query("select distinct u from User u join u.roles r where r.name = :roleName")
    java.util.List<User> findAllByRoleName(@Param("roleName") RoleName roleName);

    /**
     * Returns users created on or after the supplied threshold, ordered by
     * creation date ascending so the admin registration-trend chart can be
     * built without loading the entire user table. Pushing the time filter
     * and sort to the database lets the query touch only the new-user slice
     * instead of paying an O(users_total) JVM-side filter on every admin
     * analytics request.
     */
    java.util.List<User> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(java.time.LocalDateTime threshold);
}
