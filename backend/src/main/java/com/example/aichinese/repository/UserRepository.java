package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct u from User u left join fetch u.roles where u.email = lower(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = lower(:email)")
    Optional<User> findLockedByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(@Param("id") Long id);

    @Query("select count(distinct u) from User u join u.roles r where r.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    @EntityGraph(attributePaths = { "roles" })
    List<User> findByIdIn(Collection<Long> ids);

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
              and (:active is null or u.active = :active)
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
              and (:active is null or u.active = :active)
            """,
            nativeQuery = true)
    Page<Long> findAdminUserIds(
            @Param("roleName") String roleName,
            @Param("keyword") String keyword,
            @Param("teacherStatus") String teacherStatus,
            @Param("active") Boolean active,
            Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    @Query("select distinct u from User u join u.roles r where r.name = :roleName")
    List<User> findAllByRoleName(@Param("roleName") RoleName roleName);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);
}
