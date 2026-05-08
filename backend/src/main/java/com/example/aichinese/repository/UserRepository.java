package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct u from User u left join fetch u.roles where lower(u.email) = lower(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("select count(distinct u) from User u join u.roles r where r.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
