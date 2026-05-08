package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.enums.RoleName;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
