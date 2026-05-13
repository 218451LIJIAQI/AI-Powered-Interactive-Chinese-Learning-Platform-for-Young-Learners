package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct u from User u left join fetch u.roles where lower(u.email) = lower(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("select count(distinct u) from User u join u.roles r where r.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    java.util.List<User> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "roles" })
    java.util.List<User> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "roles" })
    Page<User> findAll(org.springframework.data.jpa.domain.Specification<User> specification, Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    @Query("select distinct u from User u join u.roles r where r.name = :roleName")
    java.util.List<User> findAllByRoleName(@Param("roleName") RoleName roleName);

    /**
     * Returns users created on or after the supplied threshold, ordered by
     * creation date ascending so the admin registration-trend chart can be
     * built without loading the entire user table. The previous
     * implementation called {@code findAllByOrderByCreatedAtDesc()} and then
     * filtered/sorted in JVM — that paid an O(users_total) cost on every
     * admin analytics request.
     */
    java.util.List<User> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(java.time.LocalDateTime threshold);
}
