package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Order;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = { "student", "student.user" })
    List<Order> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "student", "student.user" })
    Optional<Order> findByIdAndStudentUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user" })
    @Query("select o from Order o where o.id = :id and o.student.user.id = :userId")
    Optional<Order> findLockedByIdAndStudentUserId(@Param("id") Long id, @Param("userId") Long userId);
}
