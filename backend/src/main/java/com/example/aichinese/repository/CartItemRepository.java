package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByCreatedAtAsc(Long cartId);

    Optional<CartItem> findByIdAndCartStudentUserId(Long id, Long userId);

    Optional<CartItem> findByCartIdAndItemTypeAndItemId(Long cartId, String itemType, Long itemId);

    void deleteByCartId(Long cartId);
}
