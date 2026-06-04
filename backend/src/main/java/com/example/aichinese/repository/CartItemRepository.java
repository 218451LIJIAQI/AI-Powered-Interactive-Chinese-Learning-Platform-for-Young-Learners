package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByCreatedAtAsc(Long cartId);

    long countByCartId(Long cartId);

    Optional<CartItem> findByIdAndCartStudentUserId(Long id, Long userId);

    Optional<CartItem> findByCartIdAndItemTypeAndItemId(Long cartId, String itemType, Long itemId);

    @Modifying
    @Transactional
    @Query("delete from CartItem ci where ci.cart.id = :cartId")
    int deleteByCartId(@Param("cartId") Long cartId);
}
