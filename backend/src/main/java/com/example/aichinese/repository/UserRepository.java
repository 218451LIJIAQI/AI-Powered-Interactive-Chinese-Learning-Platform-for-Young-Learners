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

    Optional<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct u from User u left join fetch u.roles where lower(u.email) = lower(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("select count(distinct u) from User u join u.roles r where r.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    @EntityGraph(attributePaths = { "roles" })
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    java.util.List<User> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "roles" })
    Page<User> findAll(org.springframework.data.jpa.domain.Specification<User> specification, Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    @Query("select distinct u from User u join u.roles r where r.name = :roleName")
    java.util.List<User> findAllByRoleName(@Param("roleName") RoleName roleName);
}
