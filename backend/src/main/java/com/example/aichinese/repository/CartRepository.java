package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Cart;

import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = { "student", "student.user" })
    Optional<Cart> findByStudentUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user" })
    @Query("select c from Cart c where c.student.user.id = :userId")
    Optional<Cart> findLockedByStudentUserId(@Param("userId") Long userId);
}
