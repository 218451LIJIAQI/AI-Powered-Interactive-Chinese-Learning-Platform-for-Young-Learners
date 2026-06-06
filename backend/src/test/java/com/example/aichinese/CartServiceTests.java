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
 * Unit coverage for {@code CartService}'s idempotency edges:
 * notably that {@code clearCartIfExists} is a no-op when no cart
 * row exists, so an early-pay flow can call it unconditionally
 * without 404-ing.
 */
class CartServiceTests {

    private static final long STUDENT_USER_ID = 99L;
    private static final long STUDENT_PROFILE_ID = 199L;
    private static final long CART_ID = 123L;
    private static final long LESSON_ID = 10L;
    private static final long CART_CAPACITY = 50L;

    @Test
    void clearCartIfExistsDoesNotFailWhenCartIsMissing() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        when(cartRepository.findByStudentUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        CartService cartService = service(cartRepository, cartItemRepository);

        assertThatCode(() -> cartService.clearCartIfExists(STUDENT_USER_ID)).doesNotThrowAnyException();
        verify(cartRepository).findByStudentUserId(STUDENT_USER_ID);
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void clearCartIfExistsDeletesItemsWhenCartExists() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        Cart cart = new Cart();
        cart.setId(CART_ID);
        when(cartRepository.findByStudentUserId(STUDENT_USER_ID)).thenReturn(Optional.of(cart));

        CartService cartService = service(cartRepository, cartItemRepository);

        cartService.clearCartIfExists(STUDENT_USER_ID);

        verify(cartRepository).findByStudentUserId(STUDENT_USER_ID);
        verify(cartItemRepository).deleteByCartId(CART_ID);
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
        User user = currentUserEntity();
        SecurityUserDetails currentUser = new SecurityUserDetails(user);
        StudentProfile student = student(user);
        Cart cart = cartFor(student);
        ResolvedPurchasableItem item = paidLesson();

        when(studentLearningAccessService.requireLockedStudent(currentUser)).thenReturn(student);
        when(cartRepository.findLockedByStudentUserId(STUDENT_USER_ID)).thenReturn(Optional.of(cart));
        when(purchasableItemService.resolve("LESSON", LESSON_ID, null)).thenReturn(item);
        when(entitlementService.hasAccessToPurchasableItem(student, item)).thenReturn(false);
        when(cartItemRepository.findByCartIdAndItemTypeAndItemId(CART_ID, "LESSON", LESSON_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.countByCartId(CART_ID)).thenReturn(CART_CAPACITY, CART_CAPACITY);
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(CART_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> cartService.addItem(currentUser, new CartItemRequest("LESSON", LESSON_ID, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cart can contain at most 50 items.");

        verify(cartItemRepository, never()).save(any());
    }

    private static CartService service(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        return service(
                mock(StudentLearningAccessService.class),
                cartRepository,
                cartItemRepository,
                mock(PurchasableItemService.class),
                mock(EntitlementService.class));
    }

    private static CartService service(
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

    private static User currentUserEntity() {
        User user = new User();
        user.setId(STUDENT_USER_ID);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return user;
    }

    private static StudentProfile student(User user) {
        StudentProfile student = new StudentProfile();
        student.setId(STUDENT_PROFILE_ID);
        student.setUser(user);
        return student;
    }

    private static Cart cartFor(StudentProfile student) {
        Cart cart = new Cart();
        cart.setId(CART_ID);
        cart.setStudent(student);
        return cart;
    }

    private static ResolvedPurchasableItem paidLesson() {
        return new ResolvedPurchasableItem(
                PurchasableItemType.LESSON,
                LESSON_ID,
                "Paid Lesson",
                BigDecimal.TEN,
                "MYR",
                null,
                false,
                null);
    }
}
