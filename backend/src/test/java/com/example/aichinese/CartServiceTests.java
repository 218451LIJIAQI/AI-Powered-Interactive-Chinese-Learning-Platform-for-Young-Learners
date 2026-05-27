package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.CartItemRequest;
import com.example.aichinese.entity.Cart;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PurchasableItemType;
import com.example.aichinese.repository.CartItemRepository;
import com.example.aichinese.repository.CartRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.CartService;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.CommerceSettingsService;
import com.example.aichinese.service.EntitlementService;
import com.example.aichinese.service.PurchasableItemService;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

/**
 * Unit coverage for {@code CartService}'s idempotency edges —
 * notably that {@code clearCartIfExists} is a no-op when no cart
 * row exists, so an early-pay flow can call it unconditionally
 * without 404-ing.
 */
class CartServiceTests {

    @Test
    void clearCartIfExistsDoesNotFailWhenCartIsMissing() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        when(cartRepository.findByStudentUserId(99L)).thenReturn(Optional.empty());

        CartService cartService = service(cartRepository, cartItemRepository);

        assertThatCode(() -> cartService.clearCartIfExists(99L)).doesNotThrowAnyException();
        verify(cartRepository).findByStudentUserId(99L);
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void clearCartIfExistsDeletesItemsWhenCartExists() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        Cart cart = new Cart();
        cart.setId(123L);
        when(cartRepository.findByStudentUserId(99L)).thenReturn(Optional.of(cart));

        CartService cartService = service(cartRepository, cartItemRepository);

        cartService.clearCartIfExists(99L);

        verify(cartRepository).findByStudentUserId(99L);
        verify(cartItemRepository).deleteByCartId(123L);
    }

    @Test
    void addItemRejectsCartsAtTheServiceCapacityLimit() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        StudentLearningAccessService studentLearningAccessService = mock(StudentLearningAccessService.class);
        PurchasableItemService purchasableItemService = mock(PurchasableItemService.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        CartService cartService = service(
                studentLearningAccessService,
                cartRepository,
                cartItemRepository,
                purchasableItemService,
                entitlementService);
        SecurityUserDetails currentUser = currentUser();
        StudentProfile student = student(currentUser.getUser());
        Cart cart = new Cart();
        cart.setId(123L);
        cart.setStudent(student);
        ResolvedPurchasableItem item = new ResolvedPurchasableItem(
                PurchasableItemType.LESSON,
                10L,
                "Paid Lesson",
                BigDecimal.TEN,
                "MYR",
                null,
                false,
                null);

        when(studentLearningAccessService.requireLockedStudent(currentUser)).thenReturn(student);
        when(cartRepository.findLockedByStudentUserId(99L)).thenReturn(Optional.of(cart));
        when(purchasableItemService.resolve("LESSON", 10L, null)).thenReturn(item);
        when(entitlementService.hasAccessToPurchasableItem(student, item)).thenReturn(false);
        when(cartItemRepository.findByCartIdAndItemTypeAndItemId(123L, "LESSON", 10L)).thenReturn(Optional.empty());
        when(cartItemRepository.countByCartId(123L)).thenReturn(50L, 50L);
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(123L)).thenReturn(List.of());

        assertThatThrownBy(() -> cartService.addItem(currentUser, new CartItemRequest("LESSON", 10L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cart can contain at most 50 items.");

        verify(cartItemRepository, never()).save(any());
    }

    private CartService service(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        return service(
                mock(StudentLearningAccessService.class),
                cartRepository,
                cartItemRepository,
                mock(PurchasableItemService.class),
                mock(EntitlementService.class));
    }

    private CartService service(
            StudentLearningAccessService studentLearningAccessService,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            PurchasableItemService purchasableItemService,
            EntitlementService entitlementService) {
        return new CartService(
                studentLearningAccessService,
                cartRepository,
                cartItemRepository,
                purchasableItemService,
                entitlementService,
                mock(CommerceMoneyService.class),
                mock(CommerceSettingsService.class));
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(99L);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }

    private StudentProfile student(User user) {
        StudentProfile student = new StudentProfile();
        student.setId(199L);
        student.setUser(user);
        return student;
    }
}
